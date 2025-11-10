package hydrology;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Slope {
    private int[][] dem;
    private double cellsize;
    private int NODATA_value;

    public Slope(int[][] dem, double cellsize, int NODATA_value) {
        this.dem = dem;
        this.cellsize = cellsize;
        this.NODATA_value = NODATA_value;
    }

    /**
     * 计算每个单元格的坡度。
     * @return 返回包含坡度值的二维数组。
     */
    public double[][] calculateSlopes() {
        int rows = dem.length;
        int cols = dem[0].length;
        double[][] slope = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (dem[i][j] != NODATA_value) {
                    slope[i][j] = computeCellSlope(i, j);
                } else {
                    slope[i][j] = Double.NaN;
                }
            }
        }

        return slope;
    }

    // 计算单个单元格的坡度
    private double computeCellSlope(int row, int col) {
        double dzdx = 0.0, dzdy = 0.0;

        // 检查并计算dz/dx（东西方向的梯度）
        if (isValidCell(row, col - 1) && isValidCell(row, col + 1)) {
            if (dem[row][col - 1] != NODATA_value && dem[row][col + 1] != NODATA_value) {
                dzdx = (dem[row][col + 1] - dem[row][col - 1]) / (2 * cellsize);
            }
        }

        // 检查并计算dz/dy（南北方向的梯度）
        if (isValidCell(row - 1, col) && isValidCell(row + 1, col)) {
            if (dem[row - 1][col] != NODATA_value && dem[row + 1][col] != NODATA_value) {
                dzdy = (dem[row + 1][col] - dem[row - 1][col]) / (2 * cellsize);
            }
        }

        // 使用最大下降法计算坡度
        double maxGradient = Math.sqrt(dzdx * dzdx + dzdy * dzdy);
        return Math.toDegrees(Math.atan(maxGradient));
    }

    // 检查坐标是否有效
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < dem.length && col >= 0 && col < dem[0].length;
    }

}