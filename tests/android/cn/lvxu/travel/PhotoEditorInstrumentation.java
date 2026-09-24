package cn.lvxu.travel;

import android.app.Instrumentation;
import android.os.Bundle;

/** Focused real-bitmap runner; the main suite also calls the same assertions. */
public final class PhotoEditorInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();int status=-1;try{result.putString("stream","PASS: "+StageSixPhotoEditorTest.run(this)+" photo editor assertions\n");}catch(Throwable e){status=0;result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));}finish(status,result);}
}
