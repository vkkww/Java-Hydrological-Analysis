package hydrology;

import java.util.function.IntFunction;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class Visualizer {

    static void imgInt(int[][] data, String filename, String title, IntFunction<Color> mapper) {
        int width = data[0].length;
        int height = data.length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置标题
        g.drawString(title, 10, 20);

        // 计算最小值和最大值（排除NODATA）
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] row : data) {
            for (int value : row) {
                if (value != -9999) { // 排除NODATA值
                    if (value < min) min = value;
                    if (value > max) max = value;
                }
            }
        }

        // 绘制数据到图像中
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                if (data[i][j] == -9999) {
                    // NODATA 或无效数据，用黑色表示
                    image.setRGB(j, i, Color.BLACK.getRGB());
                } else {
                    // 归一化数据值到 [0, 1]
                    double normalizedValue = ((double)(data[i][j] - min)) / (max - min);
                    Color color = Visualizer.getPseudoColor(normalizedValue);
                    image.setRGB(j, i, color.getRGB());
                }
            }
        }

        // 释放图形上下文
        g.dispose();

        // 保存图像到文件
        saveImage(image, filename);
    }

    static void imgDbl(double[][] data, String filename, String title, ColorMapper mapper) {
        int width = data[0].length;
        int height = data.length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置标题
        g.drawString(title, 10, 20);

        // 计算最小值和最大值（排除NODATA）
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] row : data) {
            for (double value : row) {
                if (value != -9999) {
                    if (value < min) min = value;
                    if (value > max) max = value;
                }
            }
        }

        // 绘制数据到图像中
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                double value = data[i][j];
                if (value == -9999) {
                    // NODATA 或无效数据，用黑色表示
                    image.setRGB(j, i, Color.BLACK.getRGB());
                } else {
                    // 根据数据范围映射颜色
                    double normalizedValue = (value - min) / (max - min);
                    Color color = mapper.apply(normalizedValue);
                    image.setRGB(j, i, color.getRGB());
                }
            }
        }

        // 释放图形上下文
        g.dispose();

        // 保存图像到文件

        // 保存图像到文件
        saveImage(image, filename);
    }

    public static void imgBl(boolean[][] data, String filename, String title, double[][] originalData, int NODATA_value) {
        int cols = data[0].length;
        int rows = data.length;
        BufferedImage image = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置标题
        g.drawString(title, 10, 20);

        // 绘制分类结果到图像中
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (Double.isNaN(originalData[i][j]) || originalData[i][j] == NODATA_value) {
                    g.setColor(Color.BLACK); // NODATA 或无效坡度用黑色表示
                } else if (data[i][j]) {
                    g.setColor(Color.RED); // 陡峭区域用红色表示
                } else {
                    g.setColor(Color.GREEN); // 平缓区域用绿色表示
                }
                g.fillRect(j, i, 1, 1);
            }
        }

        // 释放图形上下文
        g.dispose();

        // 保存图像到文件
        saveImage(image, filename);
    }

    /**
     * 根据流向方向返回对应的颜色。
     */
    static Color getFlowDirectionColor(int direction) {
        if (direction == 0 || direction == -9999) { // 确认 -9999 是正确的 NODATA 值
            return Color.BLACK; // 对于未知的流向或NODATA
        }

        // 定义每个流向的基础颜色
        Color[] flowColors = {
                null, // 方向0未使用
                new Color(255, 0, 255), // 紫色 - 东 (1)
                new Color(0, 0, 255),   // 蓝色 - 南 (2)
                new Color(0, 255, 255), // 青色 - 南东 (4)
                new Color(0, 255, 0),   // 绿色 - 西 (8)
                new Color(255, 255, 0), // 黄色 - 西南 (16)
                new Color(255, 165, 0), // 橙色 - 北 (32)
                new Color(255, 192, 203), // 淡粉色 - 北西 (64)
                new Color(255, 0, 0)    // 红色 - 北东 (128)
        };

        // 如果只有一个流向，直接返回对应的颜色
        if ((direction & (direction - 1)) == 0) { // 测试是否只有单一bit被置位
            for (int i = 1; i < flowColors.length; i++) {
                if (direction == (1 << (i-1))) {
                    return flowColors[i];
                }
            }
        }

        // 多流向时，混合颜色
        float hueSum = 0f;
        float saturationSum = 0f;
        float brightnessSum = 0f;
        int count = 0;

        for (int i = 1; i < flowColors.length; i++) {
            if ((direction & (1 << (i-1))) != 0) {
                float[] hsb = new float[3];
                Color.RGBtoHSB(flowColors[i].getRed(), flowColors[i].getGreen(), flowColors[i].getBlue(), hsb);
                hueSum += hsb[0];
                saturationSum += hsb[1];
                brightnessSum += hsb[2];
                count++;
            }
        }

        // 如果没有找到任何有效的流向，则返回黑色
        if (count == 0) {
            return Color.BLACK;
        }

        // 计算平均值并将HSB转换回RGB并创建颜色对象
        float avgHue = hueSum / count;
        float avgSaturation = saturationSum / count;
        float avgBrightness = brightnessSum / count;

        return Color.getHSBColor(avgHue, avgSaturation, avgBrightness);
    }

    /**
     * 根据归一化的值返回对应的灰度颜色。
     */
    static Color getGrayscaleColor(double normalizedValue) {
        int grayLevel = (int) (normalizedValue * 255);
        return new Color(grayLevel, grayLevel, grayLevel);
    }

    /**
     * 保存图像到文件。
     */
    private static void saveImage(BufferedImage image, String filename) {
        try {
            File outputfile = new File(filename + ".png");
            ImageIO.write(image, "png", outputfile);
            System.out.println("图像已保存为: " + filename + ".png");
        } catch (Exception e) {
            System.out.println("保存图像失败: " + e.getMessage());
        }
    }

     //定义一个方法来根据归一化的值返回对应的伪彩色
    static Color getPseudoColor(double normalizedValue) {
        // 使用一个简单的三段式颜色映射：蓝色 -> 绿色 -> 红色
        int blue = (int) (normalizedValue * 255); // 低海拔区域为蓝色
        int green = (int) (255 * Math.abs(normalizedValue - 0.5)); // 中间海拔区域为绿色
        int red = (int) ((1 - normalizedValue) * 255); // 高海拔区域为红色

        return new Color(red, green, blue);
    }




//    public static Color getPseudoColor(double normalizedValue) {
//        // 确保 normalizedValue 在 [0, 1] 范围内
//        double clampedValue = Math.max(0, Math.min(1, normalizedValue));
//
//        // 使用一个简单的彩虹色方案作为例子
//        int red = (int) (255 * (1 - clampedValue));
//        int green = (int) (255 * clampedValue);
//        int blue = 0;
//
//        // 确保 RGB 值在 [0, 255] 范围内
//        return new Color(
//                Math.min(255, Math.max(0, red)),
//                Math.min(255, Math.max(0, green)),
//                Math.min(255, Math.max(0, blue))
//        );
//    }

    @FunctionalInterface
    interface ColorMapper {
        Color apply(double normalizedValue);
    }
}