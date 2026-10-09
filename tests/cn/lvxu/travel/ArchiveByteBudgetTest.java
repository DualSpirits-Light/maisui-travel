package cn.lvxu.travel;
public final class ArchiveByteBudgetTest {
 public static void main(String[] args)throws Exception {
  ArchiveByteBudget budget=new ArchiveByteBudget(100,64);budget.add(60);boolean rejected=false;
  try{budget.add(41);}catch(java.io.IOException e){rejected=true;}if(!rejected)throw new AssertionError("expanded total beyond limit accepted");
  budget.add(40);rejected=false;try{new ArchiveByteBudget(100,64).add(65);}catch(java.io.IOException e){rejected=true;}if(!rejected)throw new AssertionError("oversized individual entry accepted");
  ArchiveByteBudget chunks=new ArchiveByteBudget(100,64);chunks.consume(0,32);chunks.consume(32,32);rejected=false;try{chunks.consume(64,1);}catch(java.io.IOException e){rejected=true;}if(!rejected)throw new AssertionError("chunked oversized entry accepted");
  chunks.consume(0,36);rejected=false;try{chunks.consume(0,1);}catch(java.io.IOException e){rejected=true;}if(!rejected)throw new AssertionError("chunked expanded total beyond limit accepted");
  rejected=false;try{new ArchiveByteBudget(Long.MAX_VALUE,Long.MAX_VALUE).consume(Long.MAX_VALUE,1);}catch(java.io.IOException e){rejected=true;}if(!rejected)throw new AssertionError("entry accounting overflow accepted");
  System.out.println("PASS: 6 archive expanded-size assertions");
 }
}
