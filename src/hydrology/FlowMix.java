package hydrology;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.IntStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FlowMix {

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

    public FlowMix(double[][] slopes, int[][] filledDEM, boolean[][] isSteep, int NODATA_value) {
        this.slopes = slopes;
        this.filledDEM = filledDEM;
        this.isSteep = isSteep;
        this.NODATA_value = NODATA_value;
    }

    /**
     * 计算流向。
     */
    public int[][] calculateFlow() {
        int rows = slopes.length;
        int cols = slopes[0].length;
        AtomicIntegerArray flowDirections = new AtomicIntegerArray(rows * cols);

        // 使用并行流来加速计算
        IntStream.range(0, rows).parallel().forEach(i -> {
            IntStream.range(0, cols).parallel().forEach(j -> {
                if (Double.isNaN(slopes[i][j]) || slopes[i][j] == NODATA_value) {
                    // NODATA 或无效数据
                    flowDirections.set(i * cols + j, NODATA_value);
                } else {
                    // 根据是否为陡峭地区选择算法
                    if (isSteep[i][j]) {
                        // 使用D8算法
                        flowDirections.set(i * cols + j, d8(i, j));
                    } else {
                        // 使用多流向算法
                        flowDirections.set(i * cols + j, multiFlowAlgorithm(i, j));
                    }
                }
            });
        });

        // 将AtomicIntegerArray转换回二维数组
        int[][] result = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = flowDirections.get(i * cols + j);
            }
        }

        return result;
    }

    /**
     * 使用D8算法计算流向。
     */
    private int d8(int row, int col) {
        int maxDir = 0;
        double maxDiff = Double.NEGATIVE_INFINITY;

        for (int dir = 0; dir < 8; dir++) {
            int r = row + DIRECTIONS[dir][0];
            int c = col + DIRECTIONS[dir][1];

            if (isValid(r, c) && filledDEM[r][c] < filledDEM[row][col]) {
                double diff = filledDEM[row][col] - filledDEM[r][c];
                if (diff > maxDiff) {
                    maxDiff = diff;
                    maxDir = D8_DIRECTIONS[dir];
                }
            }
        }

        return maxDir;
    }

    /**
     * 使用多流向算法计算流向。
     */
    private int multiFlowAlgorithm(int row, int col) {
        double[] di = new double[8];
        double sumDi = 0.0;

        for (int dir = 0; dir < 8; dir++) {
            int r = row + DIRECTIONS[dir][0];
            int c = col + DIRECTIONS[dir][1];

            if (isValid(r, c)) {
                double Li = lineFactor(row, col, r, c);
                double tanBeta = Math.tan(Math.toRadians(slopes[r][c]));
                di[dir] = Math.pow(tanBeta,5) * Li;
                sumDi += di[dir];
            } else {
                di[dir] = 0.0;
            }
        }

        int flowDirection = 0;
        for (int dir = 0; dir < 8; dir++) {
            if (di[dir] > 0.0 && di[dir] / sumDi >= 0.5) {
                flowDirection |= 1 << dir;
            }
        }

        return flowDirection;
    }

    /**
     * 计算等高线长度加权因子Li。
     */
    private double lineFactor(int centerRow, int centerCol, int neighborRow, int neighborCol) {
        if (!isValid(neighborRow, neighborCol)) {
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
     * 检查给定的行列索引是否有效。
     */
    private boolean isValid(int row, int col) {
        return row >= 0 && row < slopes.length && col >= 0 && col < slopes[0].length &&
                !Double.isNaN(slopes[row][col]) && slopes[row][col] != NODATA_value &&
                filledDEM[row][col] != NODATA_value;
    }

    // 输出流向结果到CSV文件的方法
    public void outputToCSV(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        // 确保目录存在
        Files.createDirectories(path.getParent());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (int[] row : calculateFlow()) { // 或者使用预先计算的结果
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) writer.write(","); // 添加逗号分隔符
                    writer.write(Integer.toString(row[i]));
                }
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            throw e;
        }
    }
}