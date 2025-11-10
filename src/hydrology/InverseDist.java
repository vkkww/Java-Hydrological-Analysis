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


public class InverseDist {

    static final String SRC_CRS = "EPSG:4326"; // WGS 84 (Geographic)
    static final String DST_CRS = "EPSG:32649"; // WGS 84 / UTM zone 49N (Projected)
    static MathTransform transform;
    private int[][] dem; // DEM 数据矩阵
    double cellsize; // 栅格单元大小
    double xllcorner; // 左下角X坐标
    double yllcorner; // 左下角Y坐标
    int NODATA_value = -9999; // NODATA 值
    private double powerParameter = 2.0; // IDW 幂参数
    static Map<Integer, Station> stations = new ConcurrentHashMap<>(); // 站点信息

    static {
        try {
            CoordinateReferenceSystem srcCRS = CRS.decode(SRC_CRS);
            CoordinateReferenceSystem dstCRS = CRS.decode(DST_CRS);
            transform = CRS.findMathTransform(srcCRS, dstCRS, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize coordinate transformation", e);
        }
    }

    public InverseDist(int[][] dem, double cellsize, double xllcorner, double yllcorner) {
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
        try (BufferedReader br = new BufferedReader(new FileReader("./data/StationProperty.txt"))) {
            String line;
            br.readLine(); // 跳过标题行
            System.out.println("开始读取站点属性文件...");

            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                if (values.length == 5) {
                    int id = Integer.parseInt(values[0]); // 使用新添加的Id列
                    int stationId = Integer.parseInt(values[1]);
                    String name = values[2];
                    double lat = Double.parseDouble(values[3]);
                    double lon = Double.parseDouble(values[4]);

                    Station station = new Station(id, stationId, name, lat, lon);
                    stations.put(stationId, station); // 使用stationId作为键

//                    // 打印站点信息
//                    System.out.printf("站点ID: %d, 站点编号: %d, 名称: %s, 经度: %.4f, 纬度: %.4f%n",
//                            station.getId(), station.getStationId(), station.getName(),
//                            station.getLongitude(), station.getLatitude());
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
     * 执行IDW插值，即时保存每日结果，并计算平均值。
     */
    public void invInterpolation(String s) throws Exception {
        List<Map<Integer, Double>> dailyPrecipitationList = readRainFlowFile("./data/rainFlow.txt");
        int rows = dem.length;
        int cols = dem[0].length;

        Path outputDir = Paths.get("./result/invInterpolation");
        Files.createDirectories(outputDir); // 确保目录存在

        // 创建累积矩阵和计数矩阵用于计算平均值
        double[][] cumulativeResult = new double[rows][cols];
        int[][] countMatrix = new int[rows][cols];

        for (int day = 0; day < dailyPrecipitationList.size(); day++) {
            double[][] result = new double[rows][cols];
            Map<Integer, Double> precipitationData = dailyPrecipitationList.get(day);

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    if (dem[row][col] != NODATA_value) {
                        double interpolatedValue = calculateInterpolatedValue(row, col, precipitationData);
                        result[row][col] = interpolatedValue;
                        cumulativeResult[row][col] += interpolatedValue;
                        countMatrix[row][col]++;
                    } else {
                        result[row][col] = NODATA_value;
                    }
                }
            }

            // 即时写入结果，避免内存占用过多
            writeResultToCSV(result, outputDir.resolve("interpolation_day_" + (day + 1) + ".csv"));
            Visualizer.imgDbl(result, "./result/invInterpolation/result" + (day + 1), "Interpolation Result Day " + (day + 1), Visualizer::getGrayscaleColor);
        }

        // 计算平均值并立即写入文件
        double[][] averageResult = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                averageResult[i][j] = countMatrix[i][j] > 0 ? cumulativeResult[i][j] / countMatrix[i][j] : NODATA_value;
            }
        }
        writeResultToCSV(averageResult, outputDir.resolve("average_interpolation.csv"));

        // 可视化平均结果
        Visualizer.imgDbl(averageResult, "./result/invInterpolation/average_interpolation", "Average Interpolation Result", Visualizer::getGrayscaleColor);

        System.out.println("插值计算完成，结果已保存到指定目录。");
    }

    private double calculateInterpolatedValue(int row, int col, Map<Integer, Double> precipitationData) throws Exception {
        double interpolatedValue = 0.0;
        double sumWeights = 0.0;
        boolean isExactMatch = false;

        // 栅格点的UTM坐标可以直接从行列索引计算得出
        double gridX = col * cellsize + xllcorner;
        double gridY = row * cellsize + yllcorner;

        //System.out.println("Grid coordinates (UTM): (" + gridX + ", " + gridY + ")");

        // 定义源和目标坐标参考系统
        CoordinateReferenceSystem sourceCRS = CRS.decode(SRC_CRS); // 使用静态常量
        CoordinateReferenceSystem targetCRS = CRS.decode(DST_CRS, true); // 使用静态常量

        // 获取坐标转换工具
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);

        for (Station station : stations.values()) {
            // 创建一个DirectPosition2D来表示站点的地理坐标
            DirectPosition2D geoPosition = new DirectPosition2D(sourceCRS,station.getLatitude(),station.getLongitude());

            // 创建一个DirectPosition2D来接收转换后的UTM坐标
            DirectPosition2D utmPosition = new DirectPosition2D(targetCRS);

           // 执行坐标转换
           //transform.transform(geoPosition, utmPosition);
            // 使用静态字段 transform 进行坐标转换
            InverseDist.transform.transform(geoPosition, utmPosition);

            // 打印转换后的UTM坐标
            //System.out.println("Station " + station.getStationId() + " converted UTM coordinates: (" + utmPosition.x + ", " + utmPosition.y + ")");

            // 计算转换后的站点坐标与栅格点之间的距离
            double disX = gridX - utmPosition.x;
            double disY = gridY - utmPosition.y;
            double distance = Math.sqrt(disX * disX + disY * disY);
            //System.out.println("Distance to station " + station.getStationId() + ": " + distance);

            if (distance == 0 && precipitationData.containsKey(station.getStationId())) {
                // 如果栅格点与站点完全重合，则直接使用该站点的数据
                interpolatedValue = precipitationData.get(station.getStationId());
                isExactMatch = true;
                break;
            }

            if (precipitationData.containsKey(station.getStationId())) {
                double weight = 1.0 / Math.pow(distance, powerParameter);
                interpolatedValue += precipitationData.get(station.getStationId()) * weight;
                sumWeights += weight;
            }
        }

        if (!isExactMatch && sumWeights > 0) {
            interpolatedValue /= sumWeights;
        } else if (!isExactMatch) {
            interpolatedValue = NODATA_value;
        }

        return interpolatedValue;
    }
    /**
     * 将插值结果输出到CSV文件。
     */
    private void writeResultToCSV(double[][] result, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (int i = 0; i < result.length; i++) {
                for (int j = 0; j < result[i].length; j++) {
                    writer.write(String.valueOf(result[i][j]));
                    if (j < result[i].length - 1) writer.write(",");
                }
                writer.newLine();
            }
        }
    }

    // 站点类
    static class Station {
        private final int id;
        private final int stationId;
        private final String name;
        private final double latitude;
        private final double longitude;

        public Station(int id, int stationId, String name, double latitude, double longitude) {
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