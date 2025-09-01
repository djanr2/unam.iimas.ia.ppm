package unam.iimas.ia.ml.ppm;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;

public class DatasetCompressionCompare {

    // ===== Configuración fija (puedes cambiar estos valores) =====
    private static final String DEFAULT_FILE_NAME = "winequality-red.csv"; // en ~/Downloads
    private static final int DEFAULT_PERCENT_TRAIN = 80;         // 0..100
    private static final int PPM_ORDER = 3;                       // orden de Ppm
    private static final String COMPRESSED_EXT = ".ppm";          // extensión de salida Ppm

    public static void main(String[] args) throws Exception {
        // Parámetros opcionales por CLI: <archivo> <porcentaje> <orden_ppm>
        String fileName = (args.length >= 1) ? args[0] : DEFAULT_FILE_NAME;
        int percentTrain = (args.length >= 2) ? parseInt(args[1], DEFAULT_PERCENT_TRAIN) : DEFAULT_PERCENT_TRAIN;
        int ppmOrder = (args.length >= 3) ? parseInt(args[2], PPM_ORDER) : PPM_ORDER;

        String home = System.getProperty("user.home");
        Path filePath = Paths.get("src"+ File.separator + "main" + File.separator + "resources");

        // Rutas del archivo original y de sus derivados TRN_/TST_
        Path original = Paths.get("src"+ File.separator + "main" + File.separator + "resources" + File.separator + DEFAULT_FILE_NAME);
        String base = baseName(fileName);
        String ext = extension(fileName);
        Path trnFile = filePath.resolve("TRN_" + base + ext);
        Path tstFile = filePath.resolve("TST_" + base + ext);

        // 1) Dividir con SplitDataset (quita encabezado y escribe TRN_ y TST_)
        System.out.println("=== División de dataset ===");
        System.out.println("Archivo original: " + original.toAbsolutePath());
        System.out.println("Porcentaje train: " + percentTrain + "%");
        SplitDataset.main(new String[]{ fileName, String.valueOf(percentTrain) });

        // 2) Comprimir original, TRN y TST con Ppm
        System.out.println("\n=== Compresión con Ppm (orden " + ppmOrder + ") ===");
        Path originalCompressed = changeExtension(filePath.resolve(base + ext), COMPRESSED_EXT);
        Path trnCompressed = changeExtension(trnFile, COMPRESSED_EXT);
        Path tstCompressed = changeExtension(tstFile, COMPRESSED_EXT);

        long origSize = sizeOf(original);
        long trnSize  = sizeOf(trnFile);
        long tstSize  = sizeOf(tstFile);

        long origComp = Ppm.compressFile(original.toString(), originalCompressed.toString(), ppmOrder);
        long trnComp  = Ppm.compressFile(trnFile.toString(),    trnCompressed.toString(),    ppmOrder);
        long tstComp  = Ppm.compressFile(tstFile.toString(),    tstCompressed.toString(),    ppmOrder);

        // 3) Ratios de compresión (comprimido / original_de_ese_archivo)
        double rOrig = ratio(origComp, origSize);
        double rTrn  = ratio(trnComp,  trnSize);
        double rTst  = ratio(tstComp,  tstSize);

        // 4) Reporte y comparación
        DecimalFormat df3 = new DecimalFormat("0.000");
        DecimalFormat df2 = new DecimalFormat("0.00");

        System.out.println("\n=== Resultados ===");
        System.out.println("Original:");
        System.out.println("  Ruta: " + original);
        System.out.println("  Tamaño: " + origSize + "  Comprimido: " + origComp + "  Ratio: " + df3.format(rOrig) + "  (Reducción: " + df2.format((1 - rOrig) * 100) + "%)");
        System.out.println("TRN:");
        System.out.println("  Ruta: " + trnFile);
        System.out.println("  Tamaño: " + trnSize + "  Comprimido: " + trnComp + "  Ratio: " + df3.format(rTrn) + "  (Reducción: " + df2.format((1 - rTrn) * 100) + "%)");
        System.out.println("TST:");
        System.out.println("  Ruta: " + tstFile);
        System.out.println("  Tamaño: " + tstSize + "  Comprimido: " + tstComp + "  Ratio: " + df3.format(rTst) + "  (Reducción: " + df2.format((1 - rTst) * 100) + "%)");

        //System.out.println("\n=== Comparación de tasas (ratio) ===");
        //compareRatios("Original vs TRN", rOrig, rTrn, df3, df2);
        //compareRatios("Original vs TST", rOrig, rTst, df3, df2);

        System.out.println("\nArchivos comprimidos creados:");
        System.out.println("  " + originalCompressed.toAbsolutePath());
        System.out.println("  " + trnCompressed.toAbsolutePath());
        System.out.println("  " + tstCompressed.toAbsolutePath());
    }

    // ===== Helpers =====

    private static void compareRatios(String title, double rA, double rB, DecimalFormat df3, DecimalFormat df2) {
        double diff = rB - rA; // positivo: B comprime peor (ratio mayor); negativo: B comprime mejor
        String verdict;
        if (Math.abs(diff) < 1e-9) {
            verdict = "Igual compresibilidad.";
        } else if (diff > 0) {
            verdict = "B PEOR (mayor ratio ⇒ menos reducción).";
        } else {
            verdict = "B MEJOR (menor ratio ⇒ mayor reducción).";
        }
        System.out.println(title + ": A=" + df3.format(rA) + "  B=" + df3.format(rB) +
                "  Δ=" + df3.format(diff) + "  [" + verdict + "]  " +
                "(mejora de B vs A: " + df2.format((-diff / rA) * 100) + "%)");
    }

    private static long sizeOf(Path p) {
        File f = p.toFile();
        if (!f.exists()) throw new RuntimeException("No existe: " + p.toAbsolutePath());
        return f.length();
    }

    private static double ratio(long compressed, long original) {
        if (original <= 0) return 0.0;
        return compressed * 1.0 / original;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(dot) : "";
    }

    private static Path changeExtension(Path path, String newExt) {
        String name = path.getFileName().toString();
        String base = baseName(name);
        return path.getParent().resolve(base + newExt);
    }

    private static int parseInt(String s, int defVal) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return defVal; }
    }
}
