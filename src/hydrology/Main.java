package hydrology;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;


public class Main {

    public static void main(String[] args) {
        // 数据库连接信息
        String dbUrl = "jdbc:mysql://localhost:3306/rainfall";
        String user = "root";
        String password = "12345";

        // 初始化DEM和其它必要参数
        String demFilePath = "./data/dem.asc"; // DEM文件路径，请根据实际情况调整

        try (Connection conn = DriverManager.getConnection(dbUrl, user, password)) {
            System.out.println("成功连接到rainfall数据库");

        // 解析命令行参数或加载配置文件来获取文件路径和结果目录
        String demFilePath = getArgumentOrDefault(args, "dem", "./data/dem90m.asc");
        String resultDir = getArgumentOrDefault(args, "result", "./classifyresult");

        try (Scanner inDEM = new Scanner(new File(demFilePath))) {
            processHydrologyData(demFilePath, resultDir, inDEM);
            System.out.println("Process completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace(); // 打印堆栈跟踪以帮助调试
        }
    }

    private static void processHydrologyData(String demFilePath, String resultDir, Scanner inDEM) throws Exception {
        // 读取DEM数据
        System.out.println("开始读取DEM数据...");
        DEMReader demReader = DEMReader.readHeader(new File(demFilePath));
        demReader.readDEM(inDEM);
        int[][] dem = demReader.getDEM();
        double cellsize = demReader.getCellSize();
        double xllcorner = demReader.getXllcorner();
        double yllcorner = demReader.getYllcorner();
        int NODATA_value = demReader.getNODATA_value();
        System.out.println("DEM 数据读取完成");

//        // 可视化原始DEM数据
//        Visualizer.imgInt(dem, resultDir + "/original_dem", "Original DEM", Visualizer::getGrayscaleColor);
//        System.out.println("原始DEM数据可视化完成");
//
//        // 填洼
//        System.out.println("开始填洼...");
//        DEMFiller depressionFiller = new DEMFiller(dem, NODATA_value);
//        int[][] filledDEM = depressionFiller.fillDepressions();
//        System.out.println("填洼已完成");
//
//        // 可视化填洼后的DEM数据
//        Visualizer.imgInt(filledDEM, resultDir + "/filled_dem", "Filled DEM", Visualizer::getGrayscaleColor);
//        System.out.println("填洼后的DEM数据可视化完成");
//
//        // 计算坡度
//        System.out.println("开始计算坡度...");
//        Slope slopeCalculator = new Slope(dem, demReader.cellsize, -9999);
//        double[][] slopes = slopeCalculator.calculateSlopes();
//        System.out.println("坡度计算已完成");
//
//        // 可视化坡度数据
//        Visualizer.imgDbl(slopes, resultDir + "/slope", "Slope Data", Visualizer::getGrayscaleColor);
//        System.out.println("坡度数据可视化完成");
//
//        // 分类
//        System.out.println("开始地形分类...");
//        TerrainClassify terrainClassify = new TerrainClassify(slopes, NODATA_value);
//        boolean[][] isSteep = terrainClassify.classifyAndCalculateFlow();
//        System.out.println("地形分类已完成");
//
//        // 可视化分类结果
//        Visualizer.imgBl(isSteep, resultDir + "/classification", "Terrain Classification", slopes, NODATA_value);
//        System.out.println("分类结果可视化完成");
//
//        // 创建 FlowMix 实例并计算流向
//        System.out.println("开始混合流向计算...");
//        FlowMix flowMix = new FlowMix(slopes, filledDEM, isSteep, NODATA_value);
//        int[][] flowDirections = flowMix.calculateFlow();
//        System.out.println("混合流向计算已完成");
//
//        // 可视化流向结果
//        Visualizer.imgInt(flowDirections, resultDir + "/flow_directions", "Flow Directions", Visualizer::getGrayscaleColor);
//        System.out.println("流向结果可视化完成");
//
//        // 创建 FlowMixAcc 实例并计算累积流
//        System.out.println("开始累积流计算...");
//        FlowMixAcc flowMixAcc = new FlowMixAcc(slopes, dem, isSteep, -9999);
//        double[][] flowAccumulation = flowMixAcc.calculateFlowAccumulation();
//        System.out.println("累积流计算完成");
//
//        // 将累积流结果输出到CSV文件
//        String outputPath = "./classifyresult/FlowAccumulation.csv";
//        try {
//            flowMixAcc.outputToCSV(outputPath, flowAccumulation);
//            System.out.println("累积流数据已成功输出到 " + outputPath);
//        } catch (IOException e) {
//            System.err.println("无法输出累积流数据到文件: " + e.getMessage());
//        }
//
//        // 可视化累积流数据
//        Visualizer.imgDbl(flowAccumulation, resultDir + "/flow_accumulation", "Flow Accumulation", Visualizer::getGrayscaleColor);
//        System.out.println("累积流数据可视化完成");

//        // 反距离权重插值
//        String filePath = "./data/StationProperty.txt";
//        InverseDist.readStationProperties(filePath);
//        InverseDist inverseDist = new InverseDist(dem, cellsize, xllcorner, yllcorner);
//        inverseDist.setNODATA_value(NODATA_value);
//        inverseDist.invInterpolation(resultDir);
//        System.out.println("反距离权重插值计算完成，结果已保存到指定目录。");

//        // 创建TrendSurface实例
//        TrendSurface trendSurface = new TrendSurface(dem, 90, xllcorner, yllcorner);
//        // 设置NODATA值
//        trendSurface.setNODATA_value(-9999);
//        // 读取站点属性文件
//        String stationFilePath = "./data/StationProperty.txt";
//        TrendSurface.readStationProperties(stationFilePath);
//        // 执行趋势面插值并保存结果
//        String rainFlowFilePath = "./data/RainFlow.txt";
//        trendSurface.trendSurfaceInterpolation(rainFlowFilePath);
//        System.out.println("插值计算完成，结果已保存到指定目录。");
//        System.out.println("趋势面插值完成。");

        // 创建RBF实例
        RBF rbf = new RBF(dem, cellsize, xllcorner, yllcorner);
        rbf.setNODATA_value(NODATA_value);
        // 读取站点属性文件
        String stationPropertiesPath = "./data/StationProperty.txt";
        rbf.readStationProperties(stationPropertiesPath);
        // 执行插值计算并保存结果
        rbf.performInterpolation("./result/RBF/");
        System.out.println("RBF插值计算完成，结果已保存到指定目录。");

    }

    private static String getArgumentOrDefault(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) { // 注意索引越界问题
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}