package unam.iimas.ia.ml.ppm;
import java.io.*;
import java.util.*;

public class Ppm {

    // ======= Parámetros del alfabeto =======
    static final long TOP = 0xFFFFFFFFL; // rango de 32 bits
    static final int ALPHABET_SIZE = 256;
    static final int ESC = 256;  // escape
    static final int EOF = 257;  // fin de archivo
    static final int MAX_SYMBOL = 257; // 0..257

    // ======= API en memoria =======
    public static byte[] compressBytes(byte[] input, int order) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            compressStream(new ByteArrayInputStream(input), bos, order);
            return bos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static byte[] decompressBytes(byte[] input, int order) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            decompressStream(new ByteArrayInputStream(input), bos, order);
            return bos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    // ======= API de archivos =======
    public static long compressFile(String inFile, String outFile, int order) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(inFile));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            compressStream(in, out, order);
        }
        return new File(outFile).length();
    }

    public static long decompressFile(String inFile, String outFile, int order) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(inFile));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            decompressStream(in, out, order);
        }
        return new File(outFile).length();
    }

    // ======= Compresión por streams =======
    public static void compressStream(InputStream in, OutputStream out, int order) throws IOException {
        BitOutput bout = new BitOutput(out);
        ArithmeticEncoder enc = new ArithmeticEncoder(bout);
        PPMModel model = new PPMModel(order);

        Deque<Integer> ctx = new ArrayDeque<>();
        int b;
        while ((b = in.read()) != -1) {
            encodeSymbol(enc, model, ctx, b);
            model.update(ctx, b);
            pushContext(ctx, b, order);
        }
        encodeSymbol(enc, model, ctx, EOF);
        enc.finish();
        bout.close();
    }

    public static void decompressStream(InputStream in, OutputStream out, int order) throws IOException {
        BitInput bin = new BitInput(in);
        ArithmeticDecoder dec = new ArithmeticDecoder(bin);
        PPMModel model = new PPMModel(order);

        Deque<Integer> ctx = new ArrayDeque<>();
        while (true) {
            int sym = decodeSymbol(dec, model, ctx);
            if (sym == EOF) break;
            out.write(sym);
            model.update(ctx, sym);
            pushContext(ctx, sym, order);
        }
        out.flush();
    }

    // ======= Utilidad: comparar archivos =======
    public static boolean filesEqual(String pathA, String pathB) {
        File a = new File(pathA);
        File b = new File(pathB);
        if (a.length() != b.length()) return false;

        try (InputStream inA = new BufferedInputStream(new FileInputStream(a));
             InputStream inB = new BufferedInputStream(new FileInputStream(b))) {
            byte[] bufA = new byte[1 << 16];
            byte[] bufB = new byte[1 << 16];
            while (true) {
                int rA = inA.read(bufA);
                int rB = inB.read(bufB);
                if (rA != rB) return false;
                if (rA == -1) break; // fin
                for (int i = 0; i < rA; i++) {
                    if (bufA[i] != bufB[i]) return false;
                }
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Error comparando archivos: " + e.getMessage(), e);
        }
    }

    // ======= MAIN fijo =======
    public static void main(String[] args) throws Exception {
        int order = 3;
        String home = System.getProperty("user.home");
        String downloads = home + File.separator + "Downloads";
        downloads += File.separator + "winequality";

        String input = downloads + File.separator + "winequality-red.csv";
        String compressed = downloads + File.separator + "winequality-red.ppm";
        String decompressed = downloads + File.separator + "winequality-red.out";

        System.out.println("=== PPM demo con archivo fijo ===");
        System.out.println("Archivo original:   " + input);
        System.out.println("Archivo comprimido: " + compressed);
        System.out.println("Archivo salida:     " + decompressed);

        long origSize = new File(input).length();
        long compSize = compressFile(input, compressed, order);
        long decSize = decompressFile(compressed, decompressed, order);

        reportRatio(origSize, compSize);

        boolean ok = filesEqual(input, decompressed);
        System.out.println("Archivos coinciden: " + ok);
    }

    private static void reportRatio(long orig, long comp) {
        double ratio = (orig == 0) ? 0 : (comp * 1.0 / orig);
        double saving = (orig == 0) ? 0 : (1 - ratio) * 100.0;
        System.out.printf("Ratio: %.3f (%.2f%% reducción)\n", ratio, saving);
    }

    // ======= Núcleo PPM =======
    private static void encodeSymbol(ArithmeticEncoder enc, PPMModel model, Deque<Integer> ctx, int sym) throws IOException {
        for (int k = model.order; k >= 0; k--) {
            PPMModel.Stats stats = model.getStats(contextSuffix(ctx, k));
            if (stats == null) continue;
            if (stats.contains(sym)) {
                enc.encode(stats.cumFreq(sym), stats.cumFreq(sym + 1), stats.total);
                return;
            } else {
                enc.encode(stats.cumFreq(ESC), stats.cumFreq(ESC + 1), stats.total);
            }
        }
        int total = ALPHABET_SIZE + 1; // 0..255 + EOF
        int idx = (sym == EOF) ? ALPHABET_SIZE : sym;
        enc.encode(idx, idx + 1, total);
    }

    private static int decodeSymbol(ArithmeticDecoder dec, PPMModel model, Deque<Integer> ctx) throws IOException {
        for (int k = model.order; k >= 0; k--) {
            PPMModel.Stats stats = model.getStats(contextSuffix(ctx, k));
            if (stats == null) continue;
            int x = dec.getTarget(stats.total);
            int sym = stats.symbolFromCum(x);
            if (sym == ESC) {
                dec.decode(stats.cumFreq(ESC), stats.cumFreq(ESC + 1), stats.total);
                continue;
            } else {
                dec.decode(stats.cumFreq(sym), stats.cumFreq(sym + 1), stats.total);
                return sym;
            }
        }
        int total = ALPHABET_SIZE + 1;
        int x = dec.getTarget(total);
        int sym = (x == ALPHABET_SIZE) ? EOF : x;
        dec.decode(x, x + 1, total);
        return sym;
    }

    private static void pushContext(Deque<Integer> ctx, int sym, int order) {
        ctx.addLast(sym);
        while (ctx.size() > order) ctx.removeFirst();
    }

    private static List<Integer> contextSuffix(Deque<Integer> ctx, int k) {
        if (k == 0) return Collections.emptyList();
        if (ctx.size() < k) return null;
        ArrayList<Integer> list = new ArrayList<>(k);
        int skip = ctx.size() - k;
        int i = 0;
        for (int v : ctx) {
            if (i++ < skip) continue;
            list.add(v);
        }
        return list;
    }

    // ======= Modelo PPM =======
    static class PPMModel {
        final int order;
        private final Map<CtxKey, Node> table = new HashMap<>();
        PPMModel(int order) { this.order = order; }
        void update(Deque<Integer> ctx, int sym) {
            for (int k = 0; k <= order; k++) {
                List<Integer> key = contextSuffix(ctx, k);
                if (key == null) continue;
                Node node = table.computeIfAbsent(new CtxKey(key), kk -> new Node());
                node.add(sym);
            }
        }
        Stats getStats(List<Integer> ctx) {
            if (ctx == null) return null;
            Node node = table.get(new CtxKey(ctx));
            return (node == null) ? null : node.stats();
        }
        static class Node {
            Map<Integer,Integer> freq = new HashMap<>();
            Node() { freq.put(ESC, 1); }
            void add(int s) { freq.put(s, freq.getOrDefault(s,0)+1); }
            Stats stats() {
                int[] present = new int[MAX_SYMBOL+1];
                for (Map.Entry<Integer,Integer> e: freq.entrySet())
                    present[e.getKey()] = e.getValue();
                int[] cum = new int[MAX_SYMBOL+2];
                int run=0;
                for (int s=0;s<=MAX_SYMBOL;s++){cum[s]=run; run+=present[s];}
                cum[MAX_SYMBOL+1]=run;
                return new Stats(present,cum,run);
            }
        }
        static class Stats {
            final int[] present; final int[] cum; final int total;
            Stats(int[] p,int[] c,int t){present=p; cum=c; total=t;}
            boolean contains(int sym){return sym>=0 && sym<=MAX_SYMBOL && present[sym]>0;}
            int cumFreq(int sym){return cum[sym];}
            int symbolFromCum(int x){
                for(int s=0;s<=MAX_SYMBOL;s++) if (cum[s]<=x && x<cum[s+1]) return s;
                return MAX_SYMBOL;
            }
        }
        static class CtxKey {
            final byte[] data;
            CtxKey(List<Integer> ctx){data=new byte[ctx.size()]; for(int i=0;i<ctx.size();i++) data[i]=(byte)(ctx.get(i)&0xFF);}
            @Override public boolean equals(Object o){return o instanceof CtxKey && Arrays.equals(data,((CtxKey)o).data);}
            @Override public int hashCode(){return Arrays.hashCode(data);}
        }
    }

    // ======= Arithmetic coder =======
    static class ArithmeticEncoder {
        private long low=0,high=TOP,pending=0; private final BitOutput out;
        ArithmeticEncoder(BitOutput out){this.out=out;}
        void encode(int cumLow,int cumHigh,int total)throws IOException{
            long rng=high-low+1;
            high=low+(rng*cumHigh)/total-1;
            low=low+(rng*cumLow)/total;
            while(true){
                if((high&0x80000000L)==(low&0x80000000L)){
                    int msb=(int)(high>>>31); out.writeBit(msb);
                    while(pending-->0) out.writeBit(1-msb); pending=0;
                } else if((low&0x40000000L)!=0 && (high&0x40000000L)==0){
                    pending++; low&=0x3FFFFFFFL; high|=0x40000000L;
                } else break;
                low=(low<<1)&TOP; high=((high<<1)|1)&TOP;
            }
        }
        void finish()throws IOException{
            int msb=(int)(low>>>31); out.writeBit(msb); pending++;
            while(pending-->0) out.writeBit(1-msb); out.flush();
        }
    }

    static class ArithmeticDecoder {
        private long low=0,high=TOP,code=0; private final BitInput in;
        ArithmeticDecoder(BitInput in)throws IOException{this.in=in;for(int i=0;i<32;i++)code=(code<<1)|in.readBit();}
        int getTarget(int total){long rng=high-low+1; long val=((code-low+1)*total-1)/rng; return (int)Math.max(0,Math.min(total-1,val));}
        void decode(int cumLow,int cumHigh,int total)throws IOException{
            long rng=high-low+1; high=low+(rng*cumHigh)/total-1; low=low+(rng*cumLow)/total;
            while(true){
                if((high&0x80000000L)==(low&0x80000000L)){
                } else if((low&0x40000000L)!=0 && (high&0x40000000L)==0){
                    code^=0x40000000L; low&=0x3FFFFFFFL; high|=0x40000000L;
                } else break;
                low=(low<<1)&TOP; high=((high<<1)|1)&TOP; code=((code<<1)|in.readBit())&TOP;
            }
        }
    }

    // ======= Bit I/O =======
    static class BitOutput implements Closeable {
        private final OutputStream out; private int current=0,bits=0;
        BitOutput(OutputStream out){this.out=out;}
        void writeBit(int b)throws IOException{current=(current<<1)|(b&1);bits++;if(bits==8){out.write(current);current=0;bits=0;}}
        void flush()throws IOException{if(bits>0){current<<=(8-bits);out.write(current);}current=0;bits=0;out.flush();}
        public void close()throws IOException{flush();out.close();}
    }
    static class BitInput {
        private final InputStream in; private int current=0,bits=0;
        BitInput(InputStream in){this.in=in;}
        int readBit()throws IOException{
            if(bits==0){int nb=in.read(); if(nb==-1)nb=0; current=nb&0xFF; bits=8;}
            int b=(current>>>7)&1; current=(current<<1)&0xFF; bits--; return b;
        }
    }
}

