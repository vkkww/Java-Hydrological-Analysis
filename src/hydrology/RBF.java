package hydrology;

import org.geotools.geometry.DirectPosition2D;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RBF {

    private int[][] dem;
    private double cellSize;
    private double xllcorner;
    private double yllcorner;
    private int NODATA_value;
    private Map<Integer, Station2> stations = new HashMap<>();
    // 定义源和目标坐标参考系统
    private static final String SRC_CRS = "EPSG:4326"; // WGS84地理坐标系
    private static final String DST_CRS = "EPSG:32633"; // UTM Zone 33N，根据实际情况修改
    private static final double POWER_PARAMETER = 2.0; // 反距离权重幂参数，对于RBF可能不需要此参数
    private static MathTransform transform;

    static {
        try {
            CoordinateReferenceSystem sourceCRS = CRS.decode(SRC_CRS);
            CoordinateReferenceSystem targetCRS = CRS.decode(DST_CRS, true);
            transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
        } catch (FactoryException e) {
            throw new RuntimeException("Failed to initialize coordinate transformation", e);
        }
    }
    public RBF(int[][] dem, double cellSize, double xllcorner, double yllcorner) {
        this.dem = dem;
        this.cellSize = cellSize;
        this.xllcorner = xllcorner;
        this.yllcorner = yllcorner;
    }

    public void setNODATA_value(int NODATA_value) {
        this.NODATA_value = NODATA_value;
    }

    /**
     * 读取站点属性文件并存储到Map中。
     */
    public void readStationProperties(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); // 跳过标题行
            System.out.println("开始读取站点属性文件...");

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                if (values.length == 5) {
                    int id = Integer.parseInt(values[0]); // 使用新添加的Id列
                    int stationId = Integer.parseInt(values[1]);
                    String name = values[2];
                    double lat = Double.parseDouble(values[3]);
                    double lon = Double.parseDouble(values[4]);

                    Station2 station = new Station2(id, stationId, name, lat, lon);
                    stations.put(stationId, station); // 使用stationId作为键
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
    public List<Map<Integer, Double>> readRainFlowFile(String filePath) throws IOException {
        List<Map<Integer, Double>> dailyPrecipitationMaps = new ArrayList<>();
        Map<Integer, Integer> stationIndexMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader("./data/rainFlow.txt"))) {
            String header = br.readLine(); // 获取表头
            if (header != null) {
                String[] headers = header.split("\t");
                for (int i = 1; i < headers.length; i++) { // 跳过日期列
                    int stationId = Integer.parseInt(headers[i]);
                    stationIndexMap.put(stationId, i - 1);
                }
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                if (values.length > 1) {
                    String date = values[0]; // 假设第一列是日期
                    Map<Integer, Double> precipitationMap = new HashMap<>();

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
     * 执行径向基函数插值计算，并保存结果到CSV文件。
     */
    public void performInterpolation(String resultDir) throws Exception {
        List<Map<Integer, Double>> dailyPrecipitationMaps = readRainFlowFile("./data/rainFlow.txt");

//            // 读取降水量数据
//            String rainFlowPath = "./data/rainFlow.txt";
//            dailyPrecipitationMaps = readRainFlowFile(rainFlowPath);

        // 对每一天的数据进行插值计算
        for (int day = 0; day < dailyPrecipitationMaps.size(); day++) {
            Map<Integer, Double> precipitationData = dailyPrecipitationMaps.get(day);
            double[][] interpolatedResults = new double[dem.length][dem[0].length];

            for (int row = 0; row < dem.length; row++) {
                for (int col = 0; col < dem[row].length; col++) {
                    if (dem[row][col] != NODATA_value) {
                        double interpolatedValue = calculateInterpolatedValue(row, col, precipitationData);
                        interpolatedResults[row][col] = interpolatedValue;
//                        // 输出特定栅格点的插值结果用于调试
//                        if (row % 10 == 0 && col % 10 == 0) { // 每隔10个点输出一次
//                            System.out.printf("栅格点 (%d, %d): 插值结果 = %.2f\n", row, col, interpolatedValue);
//                        }
                    } else {
                        interpolatedResults[row][col] = NODATA_value;
                    }
                }
            }

            // 构建输出文件路径
            Path outputPath = Paths.get(resultDir, "interpolated_day_" + (day + 1) + ".csv");

            // 将插值结果写入CSV文件
            writeResultToCSV(interpolatedResults, outputPath);
            Visualizer.imgDbl(interpolatedResults, "./result/RBF/result" + (day + 1), "Interpolation Result Day " + (day + 1), Visualizer::getGrayscaleColor);
        }
    }

    private double calculateInterpolatedValue(int row, int col, Map<Integer, Double> precipitationData) throws Exception {
        // 栅格点的UTM坐标可以直接从行列索引计算得出
        double gridX = col * cellSize + xllcorner;
        double gridY = row * cellSize + yllcorner;

        double interpolatedValue = 0.0;
        double sumWeights = 0.0;
        boolean isExactMatch = false;

        // 定义源和目标坐标参考系统
        CoordinateReferenceSystem sourceCRS = CRS.decode(SRC_CRS); // 使用静态常量
        CoordinateReferenceSystem targetCRS = CRS.decode(DST_CRS, true); // 使用静态常量

        // 获取坐标转换工具
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
        for (Station2 station : stations.values()) {
            if (precipitationData.containsKey(station.getStationId())) {
                // 创建一个DirectPosition2D来表示站点的地理坐标
                DirectPosition2D geoPosition = new DirectPosition2D(sourceCRS, station.getLatitude(),station.getLongitude());

                // 创建一个DirectPosition2D来接收转换后的UTM坐标
                DirectPosition2D utmPosition = new DirectPosition2D(targetCRS);

                // 执行坐标转换
                InverseDist.transform.transform(geoPosition, utmPosition);

                // 打印转换后的UTM坐标
                //System.out.println("Station " + station.getStationId() + " converted UTM coordinates: (" + utmPosition.x + ", " + utmPosition.y + ")");

                // 计算转换后的站点坐标与栅格点之间的距离
                double disX = gridX - utmPosition.x;
                double disY = gridY - utmPosition.y;
                double distance = Math.sqrt(disX * disX + disY * disY);
                //System.out.println("Distance to station " + station.getStationId() + ": " + distance);

                if (distance == 0) {
                    // 如果栅格点与站点完全重合，则直接使用该站点的数据
                    interpolatedValue = precipitationData.get(station.getStationId());
                    isExactMatch = true;
                    break;
                } else {
                    double weight = rbf(distance); // 使用RBF函数计算权重
                    interpolatedValue += precipitationData.get(station.getStationId()) * weight;
                    sumWeights += weight;
                }
            }
        }

        if (!isExactMatch && sumWeights > 0) {
            interpolatedValue /= sumWeights;
        } else if (!isExactMatch) {
            interpolatedValue = NODATA_value;
        }

        return interpolatedValue;
    }

//    // 定义高斯RBF函数为私有静态方法
//    private static double rbf(double distance) {
//        return Math.exp(-(distance * distance) / (2 * SIGMA * SIGMA));
//    }
//
//    private static final double SIGMA = 1000000; // 可调整参数

    private static double rbf(double distance) {
        double r = 10000; // 调整这个参数
        return 1 / Math.sqrt(distance * distance + r * r);
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
    // 站点类（重命名为Station2）
    static class Station2 {
        private final int id;
        private final int stationId;
        private final String name;
        private final double latitude;
        private final double longitude;

        public Station2(int id, int stationId, String name, double latitude, double longitude) {
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

