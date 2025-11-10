package hydrology;

public class Flow {
    private int[][] dem;
    private int NODATA_value;
    private int nrows, ncols;

    public Flow(int[][] dem, int NODATA_value) {
        this.dem = dem;
        this.NODATA_value = NODATA_value;
        this.nrows = dem.length;
        this.ncols = dem[0].length;
    }

    /**
     * 计算每个单元格的流向。
     * @return 返回包含流向值的二维数组。
     */
    public int[][] calculateFlowDirection() {
        int[][] directions = {{-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}};
        int[][] flowDirection = new int[nrows][ncols];

        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                if (dem[i][j] != NODATA_value) {
                    int minElevation = Integer.MAX_VALUE;
                    int flowDir = -1;

                    for (int k = 0; k < directions.length; k++) {
                        int newRow = i + directions[k][0];
                        int newCol = j + directions[k][1];

                        if (isValidCell(newRow, newCol) && dem[newRow][newCol] < minElevation && dem[newRow][newCol] != NODATA_value) {
                            minElevation = dem[newRow][newCol];
                            flowDir = k;
                        }
                    }

                    flowDirection[i][j] = flowDir;
                } else {
                    flowDirection[i][j] = -1;
                }
            }
        }

        return flowDirection;
    }

    /**
     * 计算每个单元格的累积流。
     * @param direction 包含流向值的二维数组。
     * @return 返回包含累积流值的二维数组。
     */
    public int[][] calculateFlowAccumulation(int[][] direction) {
        int[][] flowAccumulation = new int[nrows][ncols];

        // 初始化有效的单元格流量为1（每个有效单元至少有一个单位的流量）
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                if (dem[i][j] != NODATA_value) {
                    flowAccumulation[i][j] = 1;
                }
            }
        }

        // 正向遍历
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                if (direction[i][j] != -1) {
                    int[] dir = getDirection(direction[i][j]);
                    int newRow = i + dir[0];
                    int newCol = j + dir[1];

                    if (isValidCell(newRow, newCol)) {
                        flowAccumulation[newRow][newCol] += flowAccumulation[i][j];
                    }
                }
            }
        }

        // 逆向遍历
        for (int i = nrows - 1; i >= 0; i--) {
            for (int j = ncols - 1; j >= 0; j--) {
                if (direction[i][j] != -1) {
                    int[] dir = getDirection(direction[i][j]);
                    int newRow = i + dir[0];
                    int newCol = j + dir[1];

                    if (isValidCell(newRow, newCol)) {
                        flowAccumulation[newRow][newCol] += flowAccumulation[i][j];
                    }
                }
            }
        }

        return flowAccumulation;
    }

    // 获取流向方向
    private int[] getDirection(int flowDir) {
        switch (flowDir) {
            case 0: return new int[]{-1, 0};  // 上
            case 1: return new int[]{-1, 1};  // 右上
            case 2: return new int[]{0, 1};   // 右
            case 3: return new int[]{1, 1};   // 右下
            case 4: return new int[]{1, 0};   // 下
            case 5: return new int[]{1, -1};  // 左下
            case 6: return new int[]{0, -1};  // 左
            case 7: return new int[]{-1, -1}; // 左上
            default: return new int[]{0, 0};
        }
    }

    // 检查坐标是否有效
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < nrows && col >= 0 && col < ncols;
    }
}