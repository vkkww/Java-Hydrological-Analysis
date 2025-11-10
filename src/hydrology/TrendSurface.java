package hydrology;

import org.geotools.geometry.DirectPosition2D;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TrendSurface {

    private static final String SRC_CRS = "EPSG:4326"; // WGS 84 (Geographic)
    private static final String DST_CRS = "EPSG:32649"; // WGS 84 / UTM zone 49N (Projected)
    private static MathTransform transform;
    private int[][] dem; // DEM 数据矩阵
    private double cellsize; // 栅格单元大小
    private double xllcorner; // 左下角X坐标
    private double yllcorner; // 左下角Y坐标
    private int NODATA_value = -9999; // NODATA 值
    private static Map<Integer, Station1> stations = new ConcurrentHashMap<>(); // 站点信息

    static {
        try {
            CoordinateReferenceSystem srcCRS = CRS.decode(SRC_CRS);
            CoordinateReferenceSystem dstCRS = CRS.decode(DST_CRS);
            transform = CRS.findMathTransform(srcCRS, dstCRS, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize coordinate transformation", e);
        }
    }

    public TrendSurface(int[][] dem, double cellsize, double xllcorner, double yllcorner) {
        this.dem = dem;
        this.cellsize = cellsize;
        this.xllcorner = xllcorner;
        this.yllcorner = yllcorner;
    }

    /**
     * 设置NODATA值。
     */
    public void setNODATA_value(int NODATA_value) {
        this.NODATA_value = NODATA_value;
    }

    /**
     * 读取站点属性文件并存储到Map中。
     */
    public static void readStationProperties(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // 跳过标题行
            System.out.println("开始读取站点属性文件...");

            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                if (values.length == 5) {
                    try {
                        int id = Integer.parseInt(values[0]); // 使用新添加的Id列
                        int stationId = Integer.parseInt(values[1]);
                        String name = values[2];
                        double lat = Double.parseDouble(values[3]);
                        double lon = Double.parseDouble(values[4]);

                        Station1 station1 = new Station1(id, stationId, name, lat, lon);
                        stations.put(stationId, station1); // 使用stationId作为键

                        // 打印站点信息（可选）
                        // System.out.printf("站点ID: %d, 站点编号: %d, 名称: %s, 经度: %.4f, 纬度: %.4f%n",
                        //         station1.getId(), station1.getStationId(), station1.getName(),
                        //         station1.getLongitude(), station1.getLatitude());
                    } catch (NumberFormatException e) {
                        System.out.println("警告：数据格式错误，跳过行：" + line);
                    }
                } else {
                    System.out.println("警告：跳过无效行：" + line);
                }
            }

            System.out.println("站点属性文件读取完成，共读取 " + stations.size() + " 个站点。");
        } catch (IOException e) {
            System.err.println("读取站点属性文件时发生错误: " + e.getMessage());
            throw e; // 重新抛出异常以便调用者处理
        }
    }
    /**
     * 读取rainFlow文件，返回每日降水量数据列表。
     */
    private List<Map<Integer, Double>> readRainFlowFile(String s) throws IOException {
        List<Map<Integer, Double>> dailyPrecipitationMaps = new ArrayList<>();
        Map<Integer, Integer> stationIndexMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader("./data/rainFlow.txt"))) {
            String header = br.readLine(); // 获取表头
            if (header != null) {
                String[] headers = header.split("\t");
                for (int i = 1; i < headers.length; i++) { // 跳过日期列
                    int stationId = Integer.parseInt(headers[i]);
                    stationIndexMap.put(stationId, i - 1);
                    //System.out.println("Station ID: " + stationId + ", Column Index: " + (i - 1));
                }
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                if (values.length > 1) {
                    String date = values[0]; // 假设第一列是日期
                    Map<Integer, Double> precipitationMap = new HashMap<>();
                    //System.out.println("Date: " + date);

                    for (Map.Entry<Integer, Integer> entry : stationIndexMap.entrySet()) {
                        int stationId = entry.getKey();
                        int index = entry.getValue();
                        if (index + 1 < values.length) { // +1 因为跳过了日期列
                            double precipitation = Double.parseDouble(values[index + 1]);
                            precipitationMap.put(stationId, precipitation);
                            //System.out.println("  Station ID: " + stationId + ", Precipitation: " + precipitation);
                        }
                    }
                    dailyPrecipitationMaps.add(precipitationMap);
                }
            }
        }

        return dailyPrecipitationMaps;
    }

    /**
     * 执行趋势面插值，即时保存每日结果，并计算平均值。
     */
    public void trendSurfaceInterpolation(String s) throws Exception {
        List<Map<Integer, Double>> dailyPrecipitationList = readRainFlowFile(s);
        int rows = dem.length;
        int cols = dem[0].length;

        Path outputDir = Paths.get("./result/trendSurfaceInterpolation");
        Files.createDirectories(outputDir); // 确保目录存在

        // 创建累积矩阵和计数矩阵用于计算平均值
        double[][] cumulativeResult = new double[rows][cols];
        int[][] countMatrix = new int[rows][cols];

        for (int day = 0; day < dailyPrecipitationList.size(); day++) {
            double[][] result = new double[rows][cols];
            Map<Integer, Double> precipitationData = dailyPrecipitationList.get(day);

            IntStream.range(0, rows).parallel().forEach(row -> {
                for (int col = 0; col < cols; col++) {
                    if (dem[row][col] != NODATA_value) {
                        try {
                            double interpolatedValue = calculateTrendSurfaceValue(row, col, precipitationData);
                            result[row][col] = interpolatedValue;
                            cumulativeResult[row][col] += interpolatedValue;
                            countMatrix[row][col]++;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        result[row][col] = NODATA_value;
                    }
                }
            });

            writeResultToCSV(result, outputDir.resolve("trend_interpolation_day_" + (day + 1) + ".csv"));
            Visualizer.imgDbl(result, "./result/trendSurfaceInterpolation/result" + (day + 1), "Trend Interpolation Result Day " + (day + 1), Visualizer::getGrayscaleColor);
        }

        double[][] averageResult = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                averageResult[i][j] = countMatrix[i][j] > 0 ? cumulativeResult[i][j] / countMatrix[i][j] : NODATA_value;
            }
        }
        writeResultToCSV(averageResult, outputDir.resolve("average_trend_interpolation.csv"));
        Visualizer.imgDbl(averageResult, "./result/trendSurfaceInterpolation/average_trend_interpolation", "Average Trend Interpolation Result", Visualizer::getGrayscaleColor);

        System.out.println("趋势面插值计算完成，结果已保存到指定目录。");
    }

    private double calculateTrendSurfaceValue(int row, int col, Map<Integer, Double> precipitationData) throws Exception {
        // 栅格点的UTM坐标可以直接从行列索引计算得出
        double gridX = col * cellsize + xllcorner;
        double gridY = row * cellsize + yllcorner;

        //System.out.printf("栅格点UTM坐标: X=%.4f, Y=%.4f%n", gridX, gridY);

        // 定义源和目标坐标参考系统
        CoordinateReferenceSystem sourceCRS = CRS.decode(SRC_CRS); // 使用静态常量
        CoordinateReferenceSystem targetCRS = CRS.decode(DST_CRS, true); // 使用静态常量

        // 获取坐标转换工具
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);

        // 构建设计矩阵（Design Matrix）
        List<double[]> designMatrixRows = stations.values().stream()
                .filter(station1 -> precipitationData.containsKey(station1.getStationId()))
                .map(station1 -> {
                    try {
                        // 创建一个DirectPosition2D来表示站点的地理坐标
                        DirectPosition2D geoPosition = new DirectPosition2D(sourceCRS,  station1.getLatitude(),station1.getLongitude());

                        // 创建一个DirectPosition2D来接收转换后的UTM坐标
                        DirectPosition2D utmPosition = new DirectPosition2D(targetCRS);

                        // 执行坐标转换
                        transform.transform(geoPosition, utmPosition);

                        // 打印站点地理坐标和转换后的UTM坐标
                        //System.out.printf("站点 %d 原始地理坐标: 经度=%.4f, 纬度=%.4f%n", station1.getStationId(), station1.getLongitude(), station1.getLatitude());
                        //System.out.printf("站点 %d 转换后UTM坐标: X=%.4f, Y=%.4f%n", station1.getStationId(), utmPosition.x, utmPosition.y);

                        return new double[]{1, utmPosition.x, utmPosition.y};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        if (designMatrixRows.isEmpty()) {
            System.out.println("警告：无有效观测数据，返回 NODATA_value");
            return NODATA_value;
        }

        double[][] designMatrix = designMatrixRows.toArray(new double[0][]);
        double[] observations = stations.values().stream()
                .filter(station1 -> precipitationData.containsKey(station1.getStationId()))
                .mapToDouble(station1 -> precipitationData.get(station1.getStationId()))
                .toArray();

        // 求解最小二乘法多项式系数
        double[][] coefficients = solveLeastSquares(designMatrix, observations);
        //System.out.printf("求解得到的多项式系数: [%.4f, %.4f, %.4f]%n", coefficients[0][0], coefficients[1][0], coefficients[2][0]);

        // 计算趋势面插值值
        double interpolatedValue = coefficients[0][0] + coefficients[1][0] * gridX + coefficients[2][0] * gridY;
        //System.out.printf("计算的趋势面插值值: %.4f%n", interpolatedValue);

        return interpolatedValue;
    }

    private double[][] solveLeastSquares(double[][] A, double[] b) {
        int m = A.length, n = A[0].length;
        if (m < n) throw new IllegalArgumentException("The matrix A must have at least as many rows as columns.");

        double[][] AtA = new double[n][n];
        double[] Atb = new double[n];

        // 计算AtA和Atb
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                for (int k = 0; k < m; k++) {
                    AtA[i][j] += A[k][i] * A[k][j];
                }
                AtA[j][i] = AtA[i][j]; // 对称矩阵
            }
            for (int k = 0; k < m; k++) {
                Atb[i] += A[k][i] * b[k];
            }
        }

        // 使用LU分解求解线性系统
        double[][] L = new double[n][n];
        double[][] U = new double[n][n];
        decomposeLU(AtA, L, U);

        double[] y = forwardSubstitution(L, Atb);
        double[] x = backwardSubstitution(U, y);

        return new double[][]{{x[0]}, {x[1]}, {x[2]}};
    }

    private void decomposeLU(double[][] A, double[][] L, double[][] U) {
        int n = A.length;

        for (int i = 0; i < n; i++) {
            // Initialize L to identity and U to zero
            for (int j = 0; j < n; j++) {
                L[i][j] = (i == j) ? 1.0 : 0.0;
                U[i][j] = 0.0;
            }

            for (int j = 0; j < n; j++) {
                if (i <= j) {
                    // Compute U[i][j]
                    double sum = 0.0;
                    for (int k = 0; k < i; k++) {
                        sum += L[i][k] * U[k][j];
                    }
                    U[i][j] = A[i][j] - sum;
                } else {
                    // Compute L[i][j]
                    double sum = 0.0;
                    for (int k = 0; k < j; k++) {
                        sum += L[i][k] * U[k][j];
                    }
                    L[i][j] = (A[i][j] - sum) / U[j][j];
                }
            }
        }
    }

    private double[] forwardSubstitution(double[][] L, double[] b) {
        int n = L.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < i; j++) {
                sum += L[i][j] * y[j];
            }
            y[i] = (b[i] - sum) / L[i][i];
        }
        return y;
    }

    private double[] backwardSubstitution(double[][] U, double[] y) {
        int n = U.length;
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += U[i][j] * x[j];
            }
            x[i] = (y[i] - sum) / U[i][i];
        }
        return x;
    }

    /**
     * 将插值结果输出到CSV文件。
     */
    private void writeResultToCSV(double[][] result, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (double[] rowData : result) {
                for (int j = 0; j < rowData.length; j++) {
                    writer.write(String.valueOf(rowData[j]));
                    if (j < rowData.length - 1) writer.write(",");
                }
                writer.newLine();
            }
        }
    }

    // 重命名的站点类
    private static class Station1 {
        private final int id;
        private final int stationId;
        private final String name;
        private final double latitude;
        private final double longitude;

        public Station1(int id, int stationId, String name, double latitude, double longitude) {
            this.id = id;
            this.stationId = stationId;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public int getId() { return id; }
        public int getStationId() { return stationId; }
        public String getName() { return name; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}