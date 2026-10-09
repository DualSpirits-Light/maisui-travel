package cn.lvxu.travel;

/** Brush history can be verified locally without an Android bitmap runtime. */
public final class PhotoBrushHistoryTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 public static int run(){
  checks=0;PhotoEditSession.BrushHistory h=new PhotoEditSession.BrushHistory();
  check(!h.canUndo()&&!h.canRedo(),"new history has no actions");
  h.begin();h.add(1,2,3,4,0xffEE554F,2);h.add(3,4,5,6,0xffEE554F,2);h.end();
  check(h.applied==1&&h.strokes.get(0).size()==2,"one gesture remains one undo action");
  check(h.strokes.get(0).get(0).color==0xffEE554F,"ARGB remains exact without float rounding");
  h.begin();h.add(2,3,4,5,0xff123457,3);h.end();
  h.undo();check(h.applied==1&&h.canRedo(),"undo removes only latest gesture");
  h.undo();check(h.applied==0&&!h.canUndo()&&h.canRedo(),"undo reaches original image");
  h.undo();check(h.applied==0,"undo at boundary is safe");
  h.redo();h.redo();check(h.applied==2&&!h.canRedo(),"redo restores both gestures");
  h.redo();check(h.applied==2,"redo at boundary is safe");
  h.undo();h.begin();h.add(9,9,10,10,0xffABCDEF,4);h.end();
  check(h.applied==2&&h.strokes.size()==2&&!h.canRedo(),"new gesture clears redo branch");
  check(h.strokes.get(1).get(0).color==0xffABCDEF,"new branch retains its own color");
  h.clear();check(h.applied==0&&h.strokes.isEmpty()&&!h.canRedo(),"crop or rotation can reset brush history");
  return checks;
 }
 public static void main(String[] args){System.out.println("PhotoBrushHistoryTest: "+run()+" checks passed");}
}
