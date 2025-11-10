package hydrology;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class DEMReader {
    private int nrows;
    private int ncols;
    int cellsize;
    int NODATA_value;
    double xllcorner;
    double yllcorner;
    private int[][] dem;

    public DEMReader() {}

    // 读取DEM头部信息并初始化对象
    public static DEMReader readHeader(File file) throws FileNotFoundException {
        try (Scanner inDEM = new Scanner(file)) {
            int nrows = 0, ncols = 0, cellsize = 0, NODATA_value = -9999; // 设置一个默认值
            double xllcorner = 0, yllcorner = 0;

            while (inDEM.hasNext()) {
                String key = inDEM.next();
                if (key.startsWith("ncols")) ncols = inDEM.nextInt();
                else if (key.startsWith("nrows")) nrows = inDEM.nextInt();
                else if (key.startsWith("xllcorner")) xllcorner = inDEM.nextDouble();
                else if (key.startsWith("yllcorner")) yllcorner = inDEM.nextDouble();
                else if (key.startsWith("cellsize")) cellsize = (int) inDEM.nextDouble();
                else if (key.startsWith("NODATA_value")) NODATA_value = inDEM.nextInt();
                else inDEM.nextLine(); // 忽略未识别的行
            }

            System.out.println("数据信息：\nnrows=" + nrows + "\nncols=" + ncols + "\nxllcorner=" + xllcorner + "\nyllcorner=" + yllcorner);

            return new DEMReader(nrows, ncols, cellsize, NODATA_value, xllcorner, yllcorner);
        }
    }

    // 构造函数用于创建具有指定参数的新实例
    private DEMReader(int nrows, int ncols, int cellsize, int NODATA_value, double xllcorner, double yllcorner) {
        this.nrows = nrows;
        this.ncols = ncols;
        this.cellsize = cellsize;
        this.NODATA_value = NODATA_value;
        this.xllcorner = xllcorner;
        this.yllcorner = yllcorner;
        initializeArrays();
        System.out.println("初始化数组完成");
    }

    // 初始化数组
    private void initializeArrays() {
        dem = new int[nrows][ncols];
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                dem[i][j] = NODATA_value;
            }
        }
    }

    // 读取DEM数据（跳过头部信息）
    public void readDEM(Scanner inDEM) {
        // 跳过头部信息
        for (int i = 0; i < 6 && inDEM.hasNextLine(); i++) {
            String line = inDEM.nextLine();
            System.out.println("Header line " + i + ": " + line); // 打印头部信息以便验证
        }

        // 正常初始化dem数组
        for (int i = 0; i < nrows && inDEM.hasNextLine(); i++) {
            String line = inDEM.nextLine().trim(); // 读取一行并去除首尾空白
            if (line.isEmpty()) continue; // 忽略空行
            Scanner lineScanner = new Scanner(line);
            lineScanner.useDelimiter("\\s+"); // 使用一个或多个空白字符作为分隔符

            for (int j = 0; j < ncols && lineScanner.hasNext(); j++) {
                try {
                    dem[i][j] = lineScanner.nextInt();
                } catch (NumberFormatException e) {
                    System.out.println("非数字数据在行 " + i + " 列 " + j + ": " + lineScanner.next());
                    dem[i][j] = NODATA_value;
                }

//                // 调试信息：打印指定范围的数据
//                if (i >= 5000 && i <= 5020 && j >= 4500 && j <= 4520) {
//                    System.out.printf(" [%d][%d] = %d\n", i, j, dem[i][j]);
//                }
            }
            lineScanner.close();
        }
        //System.out.println("DEM 数据读取完成");
    }

    // 获取DEM数据
    public int[][] getDEM() {
        return dem;
    }

    // 获取DEM的元数据
    public String getMetadata() {
        return String.format("nrows=%d, ncols=%d, cellsize=%d, NODATA_value=%d, xllcorner=%.2f, yllcorner=%.2f",
                nrows, ncols, cellsize, NODATA_value, xllcorner, yllcorner);
    }

    public int getCellSize() {
        return cellsize;
    }

    public double getXllcorner() {
        return xllcorner;
    }

    public double getYllcorner() {
        return yllcorner;
    }

    public int getNODATA_value() {
        return NODATA_value;
    }
}