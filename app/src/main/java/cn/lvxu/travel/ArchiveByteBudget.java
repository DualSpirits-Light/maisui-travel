package cn.lvxu.travel;
import java.io.IOException;
/** Expanded entry size accounting, independent of ZIP compression ratio. */
final class ArchiveByteBudget {
 private final long totalLimit,entryLimit;private long total;
 ArchiveByteBudget(long totalLimit,long entryLimit){this.totalLimit=totalLimit;this.entryLimit=entryLimit;}
 void add(long bytes)throws IOException {consume(0,bytes);}
 void consume(long entryBytes,long bytes)throws IOException {if(entryBytes<0||bytes<0||entryBytes>entryLimit||bytes>entryLimit-entryBytes||bytes>totalLimit-total)throw new IOException("备份文件过大");total+=bytes;}
}
