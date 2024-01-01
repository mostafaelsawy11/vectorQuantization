import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

public class VectorQuantizationGUI extends JFrame {
    private JTextField codeBookField, widthField, heightField;
    private JButton selectFileButton, compressButton, decompressButton;
    private String selectedFilePath;
    private JTextArea selectedFileArea;

    public static int height, width, heightVector, widthVector, codeBookSize;
    public static List<float[][][]> imageVectors = new ArrayList<>();
    public static float[][][] originalImage;
    public static List<float[][][]> codeBooks = new ArrayList<>();
    public static Map<float[][][], String> codeBookMap = new HashMap<>();
    public static String[][] encodedImage;
    public static Map<float[][][], List<float[][][]>> nearestVectors = new HashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new VectorQuantizationGUI().setVisible(true);
            }
        });
    }

    public VectorQuantizationGUI() {
        setTitle("Vector Quantization GUI");
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(0, 2));

        addField("Width:", widthField = new JTextField());
        addField("Height:", heightField = new JTextField());
        addField("CodeBook Size:", codeBookField = new JTextField());

        selectFileButton = new JButton("Select File");
        compressButton = new JButton("Compress");
        decompressButton = new JButton("Decompress");
        selectedFileArea = new JTextArea("Selected File: ");
        selectedFileArea.setEditable(false);
        selectedFileArea.setLineWrap(true);
        selectedFileArea.setWrapStyleWord(true);


        selectFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
            int result = fileChooser.showOpenDialog(VectorQuantizationGUI.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                selectedFilePath = selectedFile.getAbsolutePath();
                selectedFileArea.setText("Selected File: " + selectedFilePath);
            }
        });


        compressButton.addActionListener(e -> {
            codeBookSize = Integer.parseInt(codeBookField.getText());
            widthVector = Integer.parseInt(widthField.getText());
            heightVector = Integer.parseInt(heightField.getText());

            compressImage();
        });

        decompressButton.addActionListener(e -> decompressImage());

        add(selectFileButton);
        add(selectedFileArea);
        // add(new JLabel());
        add(compressButton);
        add(decompressButton);
    }

    private void addField(String label, JTextField field) {
        add(new JLabel(label));
        add(field);
    }

    public static float[][][] readImage(String filename) {
        BufferedImage img = null;
        File f = null;
        try {
            f = new File(filename);
            img = ImageIO.read(f);
        } catch (IOException e) {
            System.out.println(e);
        }
        height = img.getHeight();
        width = img.getWidth();
        float[][][] pixels = new float[height][width][3];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = img.getRGB(j, i);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb) & 0xFF;
                pixels[i][j][0] = r;
                pixels[i][j][1] = g;
                pixels[i][j][2] = b;

            }
        }
        return pixels;
    }

    public static List<float[][][]> divideImage() {
        List<float[][][]> vectors = new ArrayList<>();
        height = originalImage.length;
        width = originalImage[0].length;
        int h = height % heightVector;
        int w = width % widthVector;
        if ((h != 0) || (w != 0)) {
            height -= h;
            width -= w;
            float[][][] newImage = new float[height][width][3];
            for (int i = 0; i < height; i++)
                for (int j = 0; j < width; j++)
                    newImage[i][j] = originalImage[i][j];
            originalImage = newImage;
        }
        for (int i = 0; i < height; i += heightVector) {
            for (int j = 0; j < width; j += widthVector) {
                float[][][] vector = new float[heightVector][widthVector][3];
                h = i;
                for (int x = 0; x < heightVector; x++, h++) {
                    w = j;
                    for (int y = 0; y < widthVector; y++, w++) {
                        vector[x][y] = originalImage[h][w];
                    }
                }
                vectors.add(vector);
            }
        }
        return vectors;
    }

    public static float[][][] getAverageVector(List<float[][][]> vectors) {
        float[][][] averageVector = new float[heightVector][widthVector][3];
        for (int i = 0; i < vectors.size(); i++) {
            for (int j = 0; j < heightVector; j++) {
                for (int k = 0; k < widthVector; k++) {
                    for(int l = 0;l<3 ; l ++) {
                        averageVector[j][k][l] += vectors.get(i)[j][k][l];
                    }
                }
            }
        }
        for (int i = 0; i < averageVector.length; i++) {
            for (int j = 0; j < averageVector[i].length; j++) {
                for(int l = 0 ; l <3 ; l ++) {
                    averageVector[i][j][l] /= vectors.size();
                }
            }
        }
        return averageVector;
    }

    public static List<float[][][]> split(List<float[][][]> codeBooks) {
        List<float[][][]> temp = new ArrayList<>();
        for (int i = 0; i < codeBooks.size(); i++) {
            float[][][] temp1 = new float[heightVector][widthVector][3];
            float[][][] temp2 = new float[heightVector][widthVector][3];
            for (int j = 0; j < heightVector; j++) {
                for (int k = 0; k < widthVector; k++) {
                    for(int l = 0 ;l<3 ; l ++) {
                        temp1[j][k][l]= (float) Math.floor(codeBooks.get(i)[j][k][l]);
                        temp2[j][k][l] = (float) Math.ceil(codeBooks.get(i)[j][k][l]);
                        if (temp1[j][k][l] == temp2[j][k][l]) {
                            temp1[j][k][l] -= 1;
                            temp2[j][k][l] += 1;
                        }
                    }
                }

            }
            temp.add(temp1);
            temp.add(temp2);
        }
        codeBooks = temp;
        return codeBooks;
    }

    public static void getNearestVectors() {
        for (int i = 0; i < codeBooks.size(); i++) {
            nearestVectors.put(codeBooks.get(i), new ArrayList<>());
        }
        for (float[][][] imageVector : imageVectors) {
            float[][][] temp = new float[heightVector][widthVector][3];
            List<float[][][]> vectors = new ArrayList<>();
            temp = imageVector;
            float min = 1000000000;
            int inde = -1;
            for (int j = 0; j < codeBooks.size(); j++) {
                float[][][] temp1 = new float[heightVector][widthVector][3];
                temp1 = codeBooks.get(j);
                float sum = 0;
                for (int k = 0; k < heightVector; k++) {
                    for (int l = 0; l < widthVector; l++) {
                        for(int z = 0 ; z<3 ; z++) {
                            sum += (float) Math.abs(temp[k][l][z] - temp1[k][l][z]);
                        }
                    }
                }
                if (sum < min) {
                    min = sum;
                    inde = j;
                }

            }
            vectors.add(temp);
            if (nearestVectors.containsKey(codeBooks.get(inde))) {
                vectors.addAll(nearestVectors.get(codeBooks.get(inde)));
            }
            nearestVectors.put(codeBooks.get(inde), vectors);
        }
    }

    public static void Quantize() {
        boolean flag = true;
        while (codeBooks.size() < codeBookSize) {
            codeBooks.clear();
            for (float[][][] v : nearestVectors.keySet()) {
                float[][][] temp = new float[heightVector][widthVector][3];
                temp = getAverageVector(nearestVectors.get(v));
                codeBooks.add(temp);
            }
            codeBooks = split(codeBooks);
            nearestVectors.clear();
            getNearestVectors();
        }

        List<float[][][]> t = new ArrayList<>();
        while (flag) {
            flag = false;
            for (float[][][] v : nearestVectors.keySet()) {
                float[][][] temp = getAverageVector(nearestVectors.get(v));
                if (!containsSameElements(codeBooks, temp)) {
                    t.add(temp);
                    flag = true;
                } else {
                    t.add(v);
                }
            }
            codeBooks = new ArrayList<>(t);
            t.clear();
            nearestVectors.clear();
            getNearestVectors();
        }
    }

    private static boolean containsSameElements(List<float[][][]> list1, float[][][] array) {
        for (float[][][] arr : list1) {
            if (Arrays.deepEquals(arr, array)) {
                return true;
            }
        }
        return false;
    }

    public static void encode() {
        int numOfBits = (int) (Math.log(codeBookSize) / Math.log(2));
        for (int i = 0; i < codeBooks.size(); i++) {
            String binary = Integer.toBinaryString(i);
            while (binary.length() < numOfBits) {
                binary = "0" + binary;
            }
            codeBookMap.put(codeBooks.get(i), binary);
        }

    }

    public static void compress() {
        int h = height / heightVector;
        int w = width / widthVector;
        encodedImage = new String[h][w];
        for (int i = 0; i < imageVectors.size(); i++) {
            float[][][] temp = imageVectors.get(i);
            float[][][] codeBook = new float[heightVector][widthVector][3];

            for (float[][][] v : nearestVectors.keySet()) {
                if (nearestVectors.get(v).contains(temp)) {
                    codeBook = v;
                    break;
                }
            }

            String code = codeBookMap.get(codeBook);
            int y = i / w;
            int z = i % w;
            encodedImage[y][z] = code;
        }
    }

    public static void writeToBinFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("compressed.bin"))) {
            // Write encoded image, height, width, height vector, width vector, and code
            // book map
            oos.writeObject(encodedImage);
            oos.writeInt(height);
            oos.writeInt(width);
            oos.writeInt(heightVector);
            oos.writeInt(widthVector);
            oos.writeObject(codeBookMap);
            System.out.println("Binary file written successfully.");
        } catch (Exception e) {
            System.out.println("Error writing to binary file: " + e.getMessage());
        }
    }

    public static void readFromBinFile(String fileName) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            // Read encoded image, height, width, height vector, width vector, and code book
            // map
            encodedImage = (String[][]) ois.readObject();
            height = ois.readInt();
            width = ois.readInt();
            heightVector = ois.readInt();
            widthVector = ois.readInt();
            codeBookMap = (Map<float[][][], String>) ois.readObject();

            System.out.println("Binary file read successfully.");
        } catch (Exception e) {
            System.out.println("Error reading from binary file: " + e.getMessage());
        }
    }

    public static void saveImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < encodedImage.length; i++) {
            for (int j = 0; j < encodedImage[i].length; j++) {
                String code = encodedImage[i][j];
                float[][][] codeBook = new float[heightVector][widthVector][3];
                for (float[][][] v : codeBookMap.keySet()) {
                    if (codeBookMap.get(v).equals(code)) {
                        codeBook = v;
                        break;
                    }
                }
                for (int k = 0; k < heightVector; k++) {
                    for (int l = 0; l < widthVector; l++) {
                        int r =(int) codeBook[k][l][0];
                        int g = (int)codeBook[k][l][1];
                        int b = (int)codeBook[k][l][2];
                        int rgb = (r << 16) | (g << 8) | b;
                        img.setRGB(j * widthVector + l, i * heightVector + k, rgb);
                    }
                }
            }
        }
        File f = null;
        try {
            f = new File("compressed.jpg");
            ImageIO.write(img, "jpg", f);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public void compressImage() {
        originalImage = readImage(selectedFilePath);
        imageVectors = divideImage();
        float[][][] averageVector = getAverageVector(imageVectors);
        codeBooks.add(averageVector);
        codeBooks = split(codeBooks);
        getNearestVectors();
        Quantize();
        encode();
        compress();
        writeToBinFile();
    }

    public static void decompressImage() {
        readFromBinFile("compressed.bin");
        saveImage();
    }
}
