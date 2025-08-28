package unam.iimas.ia.ml.ppm;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SplitDataset {

    // ====== Config por defecto (puedes ajustarlos) ======
    private static final String DEFAULT_FILE_NAME = "winequality-red.csv"; // archivo en ~/Downloads
    private static final int DEFAULT_PERCENT_TRAIN = 50;         // 0..100
    private static final boolean SHUFFLE = false;                // true para mezclar antes de dividir

    public static void main(String[] args) {
        try {
            // 1) Resolver rutas
            String userHome = System.getProperty("user.home");
            Path downloads = Paths.get(userHome, "Downloads" +File.separator + "winequality");

            // Nombre de archivo y % de entrenamiento (opcionales por CLI)
            String inputFileName = (args.length >= 1) ? args[0] :  DEFAULT_FILE_NAME;
            int percentTrain = (args.length >= 2) ? parsePercent(args[1]) : DEFAULT_PERCENT_TRAIN;

            Path inputPath = downloads.resolve(inputFileName);

            if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath)) {
                System.err.println("No se encontró el archivo: " + inputPath.toAbsolutePath());
                System.exit(1);
            }
            if (percentTrain < 0 || percentTrain > 100) {
                System.err.println("El porcentaje debe estar entre 0 y 100");
                System.exit(1);
            }

            // 2) Leer todas las líneas (UTF-8)
            List<String> all = Files.readAllLines(inputPath, StandardCharsets.UTF_8);
            if (all.isEmpty()) {
                System.err.println("El archivo está vacío: " + inputPath.toAbsolutePath());
                System.exit(1);
            }

            // 3) Quitar encabezado (primera línea)
            String header = all.get(0); // por si lo necesitas
            List<String> rows = new ArrayList<>(all.subList(1, all.size())); // sin encabezado

            // 4) (Opcional) Mezclar filas antes de dividir
            if (SHUFFLE) {
                Collections.shuffle(rows, new Random(42)); // semilla fija para reproducibilidad
            }

            int total = rows.size();
            int nTrain = (int) Math.round(total * (percentTrain / 100.0));
            int nTest  = total - nTrain;

            // 5) Armar nombres de salida con prefijos TRN_ y TST_
            String baseName = baseName(inputFileName);
            String ext = extension(inputFileName);
            String trainName = "TRN_" + baseName + ext;
            String testName  = "TST_" + baseName + ext;

            Path trainPath = downloads.resolve(trainName);
            Path testPath  = downloads.resolve(testName);

            // 6) Escribir archivos (sin encabezado, tal como pediste)
            writeLines(trainPath, rows.subList(0, nTrain));
            writeLines(testPath,  rows.subList(nTrain, total));

            // 7) Reporte
            System.out.println("Archivo original : " + inputPath.toAbsolutePath());
            System.out.println("Encabezado       : \"" + header + "\" (removido)");
            System.out.println("Total sin header : " + total + " renglones");
            System.out.println("Porcentaje train : " + percentTrain + "%");
            System.out.println("→ Train (" + nTrain + "): " + trainPath.toAbsolutePath());
            System.out.println("→ Test  (" + nTest  + "): " + testPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error de E/S: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(3);
        }
    }

    // ---- Helpers ----

    private static int parsePercent(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Porcentaje inválido: " + s);
        }
    }

    private static void writeLines(Path path, List<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(dot) : ""; // incluye el punto
    }
}
