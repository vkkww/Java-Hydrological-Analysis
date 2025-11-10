package hydrology;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class DEMFiller {
    private static int[][] dem; // 原始DEM数据
    private static int NODATA_value;
    private static int rows;
    private static int cols;

    public DEMFiller(int[][] dem, int NODATA_value) {
        this.dem = dem;
        this.NODATA_value = NODATA_value;
        this.rows = dem.length;
        this.cols = dem[0].length;
    }

    /**
     * 执行洼地填充算法。
     * @return 返回填洼后的DEM数据副本。
     */
    public static int[][] fill() {
        int[][] filledDEM = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(dem[i], 0, filledDEM[i], 0, cols);
        }

        ForkJoinPool pool = new ForkJoinPool();
        FillTask task = new FillTask(filledDEM, 0, rows - 1, 0, cols - 1, rows, cols);
        pool.invoke(task);

        return filledDEM;
    }

    private static class FillTask extends RecursiveTask<Void> {
        private final int[][] dem;
        private final int startRow, endRow;
        private final int startCol, endCol;
        private final int rows, cols;

        public FillTask(int[][] dem, int startRow, int endRow, int startCol, int endCol, int rows, int cols) {
            this.dem = dem;
            this.startRow = startRow;
            this.endRow = endRow;
            this.startCol = startCol;
            this.endCol = endCol;
            this.rows = rows;
            this.cols = cols;
        }

        @Override
        protected Void compute() {
            if ((endRow - startRow + 1) * (endCol - startCol + 1) <= 1000) { // 如果子区域足够小，则直接处理
                processRegion(startRow, endRow, startCol, endCol);
            } else {
                int midRow = (startRow + endRow) / 2;
                int midCol = (startCol + endCol) / 2;

                invokeAll(
                        new FillTask(dem, startRow, midRow, startCol, midCol, rows, cols),
                        new FillTask(dem, startRow, midRow, midCol + 1, endCol, rows, cols),
                        new FillTask(dem, midRow + 1, endRow, startCol, midCol, rows, cols),
                        new FillTask(dem, midRow + 1, endRow, midCol + 1, endCol, rows, cols)
                );
            }
            return null;
        }

        private void processRegion(int startRow, int endRow, int startCol, int endCol) {
            PriorityQueue<int[]> queue = new PriorityQueue<>(new Comparator<int[]>() {
                @Override
                public int compare(int[] o1, int[] o2) {
                    return Integer.compare(o1[2], o2[2]);
                }
            });

            // 将非NODATA_value的边界网格插入优先队列
            for (int i = startRow; i <= endRow; i++) {
                for (int j = startCol; j <= endCol; j++) {
                    if ((i == startRow || i == endRow || j == startCol || j == endCol) && dem[i][j] != NODATA_value) {
                        queue.add(new int[]{i, j, dem[i][j]});
                    }
                }
            }

            // 主循环
            while (!queue.isEmpty()) {
                int[] current = queue.poll();
                int row = current[0];
                int col = current[1];
                int height = current[2];

                // 遍历邻接网格
                for (int[] direction : getDirections(rows, cols)) {
                    int newRow = row + direction[0];
                    int newCol = col + direction[1];

                    if (isValidCell(newRow, newCol, startRow, endRow, startCol, endCol) && dem[newRow][newCol] != NODATA_value) {
                        int newHeight = Math.max(dem[newRow][newCol], height);
                        if (dem[newRow][newCol] < newHeight) { // 只有当新高度大于当前高度时才更新
                            dem[newRow][newCol] = newHeight;
                            queue.add(new int[]{newRow, newCol, newHeight});
                        }
                    }
                }
            }
        }

        // 获取8个方向的偏移量
        private static int[][] getDirections(int rows, int cols) {
            return new int[][]{
                    {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                    {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
            };
        }

        // 检查坐标是否有效
        private boolean isValidCell(int row, int col, int startRow, int endRow, int startCol, int endCol) {
            return row >= startRow && row <= endRow && col >= startCol && col <= endCol;
        }
    }

    /**
     * 输出填洼后的DEM到文件，并打印最低值和最高值。
     */
    public static void outputFilledDEM() {
        int[][] filledDEM = fill();

        // 计算最低值和最高值
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (filledDEM[i][j] != NODATA_value) {
                    if (filledDEM[i][j] < min) {
                        min = filledDEM[i][j];
                    }
                    if (filledDEM[i][j] > max) {
                        max = filledDEM[i][j];
                    }
                }
            }
        }

        // 输出结果到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./result/output.txt"))) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    writer.write(filledDEM[i][j] == NODATA_value ? "NODATA" : String.valueOf(filledDEM[i][j]));
                    writer.write(j == cols - 1 ? "\n" : " ");
                }
            }
            // 写入最低值和最高值
            writer.write("Min Value: " + min + "\n");
            writer.write("Max Value: " + max + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 打印最低值和最高值
        System.out.println("Min Value: " + min);
        System.out.println("Max Value: " + max);
    }
}