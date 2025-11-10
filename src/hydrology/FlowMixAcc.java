package hydrology;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.IntStream;

public class FlowMixAcc {
    private static final int[] D8_DIRECTIONS = {64, 128, 1, 2, 4, 8, 16, 32};
    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    private double[][] slopes;
    private int[][] filledDEM;
    private boolean[][] isSteep;
    private int NODATA_value;
    private int nrows;
    private int ncols;

    public FlowMixAcc(double[][] slopes, int[][] filledDEM, boolean[][] isSteep, int NODATA_value) {
        this.slopes = slopes;
        this.filledDEM = filledDEM;
        this.isSteep = isSteep;
        this.NODATA_value = NODATA_value;
        this.nrows = slopes.length;
        this.ncols = slopes[0].length;
    }

    /**
     * 计算累积流。
     */
    public double[][] calculateFlowAccumulation() {
        double[][] flowAcc = new double[nrows][ncols];

        // 初始化有效的单元格流量为1（每个有效单元至少有一个单位的流量）
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                if (isValidCell(i, j)) {
                    flowAcc[i][j] = 1;
                }
            }
        }

        // 使用并行流来加速计算
        IntStream.range(0, nrows).parallel().forEach(i -> {
            IntStream.range(0, ncols).parallel().forEach(j -> {
                if (isValidCell(i, j)) {
                    updateFlowAccumulation(flowAcc, i, j);
                }
            });
        });

        return flowAcc;

    }

    /**
     * 更新累积流值。
     */
    private void updateFlowAccumulation(double[][] flowAccumulation, int row, int col) {
        double[] di = new double[8];
        double sumDi = 0.0;

        // 根据是否为陡峭地区选择算法
        if (isSteep[row][col]) {
            d8Algorithm(row, col, flowAccumulation);
        } else {
            multiFlowAlgorithm(row, col, flowAccumulation, di, sumDi);
        }
    }

    /**
     * 使用D8算法更新累积流。
     */
    private void d8Algorithm(int row, int col, double[][] flowAccumulation) {
        int maxDir = 0;
        double maxDiff = Double.NEGATIVE_INFINITY;

        for (int dir = 0; dir < 8; dir++) {
            int r = row + DIRECTIONS[dir][0];
            int c = col + DIRECTIONS[dir][1];

            if (isValidCell(r, c) && filledDEM[r][c] < filledDEM[row][col]) {
                double diff = filledDEM[row][col] - filledDEM[r][c];
                if (diff > maxDiff) {
                    maxDiff = diff;
                    maxDir = dir;
                }
            }
        }

        if (maxDiff != Double.NEGATIVE_INFINITY) {
            int newRow = row + DIRECTIONS[maxDir][0];
            int newCol = col + DIRECTIONS[maxDir][1];
            if (isValidCell(newRow, newCol)) {
                flowAccumulation[newRow][newCol] += flowAccumulation[row][col];
            }
        }
    }

    /**
     * 使用多流向算法更新累积流。
     */
    private void multiFlowAlgorithm(int row, int col, double[][] flowAccumulation, double[] di, double sumDi) {
        for (int dir = 0; dir < 8; dir++) {
            int r = row + DIRECTIONS[dir][0];
            int c = col + DIRECTIONS[dir][1];

            if (isValidCell(r, c)) {
                double Li = lineFactor(row, col, r, c);
                double tanBeta = Math.tan(Math.toRadians(slopes[r][c]));
                di[dir] = Math.pow(tanBeta, 5) * Li;
                sumDi += di[dir];
            } else {
                di[dir] = 0.0;
            }
        }

        for (int dir = 0; dir < 8; dir++) {
            if (di[dir] > 0.0 && di[dir] / sumDi >= 0.5) {
                int newRow = row + DIRECTIONS[dir][0];
                int newCol = col + DIRECTIONS[dir][1];

                if (isValidCell(newRow, newCol)) {
                    flowAccumulation[newRow][newCol] += flowAccumulation[row][col] * (di[dir] / sumDi);
                }
            }
        }
    }

    /**
     * 计算等高线长度加权因子Li。
     */
    private double lineFactor(int centerRow, int centerCol, int neighborRow, int neighborCol) {
        if (!isValidCell(neighborRow, neighborCol)) {
            return 0.0;
        }

        int heightDiff = filledDEM[neighborRow][neighborCol] - filledDEM[centerRow][centerCol];
        if (heightDiff <= 0) {
            if ((Math.abs(neighborRow - centerRow) == 1 && Math.abs(neighborCol - centerCol) == 1)) {
                return 0.5 * Math.sqrt(2); // 对角线距离
            } else if ((Math.abs(neighborRow - centerRow) == 1 || Math.abs(neighborCol - centerCol) == 1)) {
                return 0.5; // 直线距离
            } else {
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }

    /**
     * 检查目标单元格是否有效。
     */
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < nrows && col >= 0 && col < ncols &&
                !Double.isNaN(slopes[row][col]) && slopes[row][col] != NODATA_value &&
                filledDEM[row][col] != NODATA_value;
    }

    /// 输出累积流结果到CSV文件的方法
    public void outputToCSV(String filePath, double[][] flowAccumulation) throws IOException {
        Path path = Paths.get(filePath);

        // 确保目录存在
        Files.createDirectories(path.getParent());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (int i = 0; i < nrows; i++) {
                for (int j = 0; j < ncols; j++) {
                    if (j > 0) writer.write(",");
                    if (!isValidCell(i, j)) {
                        writer.write(Integer.toString(NODATA_value)); // 使用 NODATA_value 变量
                    } else {
                        writer.write(Double.toString(flowAccumulation[i][j]));
                    }
                }
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            throw e;
        }
    }
}