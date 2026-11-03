package org.meristem.rvcprobe;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real ONNX model weights but SYNTHETIC stage input. Not a full voice-conversion test. */
@RunWith(AndroidJUnit4.class)
public class OnnxStagesTest {
    static final String TAG = "RVC_ONNX_PROBE";
    static final String DIR = "rvc-ort-probe";
    private Context ctx() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private File model(String name) {
        File p = new File(new File(ctx().getFilesDir(), DIR), name);
        assertTrue("Missing test model " + p.getAbsolutePath(), p.exists());
        return p;
    }
    private void line(String text) throws Exception {
        Log.i(TAG,text);
        File dir = new File(ctx().getFilesDir(), DIR);
        try(PrintWriter out = new PrintWriter(new FileOutputStream(new File(dir, "metrics.txt"),true))){
            out.println(System.currentTimeMillis() + " " + text);
        }
    }
    private long now() {return SystemClock.elapsedRealtimeNanos();}
    private long ms(long t) {return (now()-t)/1_000_000L;}
    private OrtSession open(OrtEnvironment env, String file) throws Exception{
        return open(env, file, "cpu");
    }
    private OrtSession open(OrtEnvironment env, String file, String provider) throws Exception{
        OrtSession.SessionOptions opt = new OrtSession.SessionOptions();
        opt.setIntraOpNumThreads(2);
        opt.setInterOpNumThreads(1);
        opt.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        line("AVAILABLE_PROVIDERS "+OrtEnvironment.getAvailableProviders());
        if (provider.equals("xnnpack")) opt.addXnnpack(Map.of("intra_op_num_threads","2"));
        if (provider.equals("nnapi")) opt.addNnapi();
        if (provider.equals("webgpu")) opt.addWebGPU(Map.of());
        long t = now();
        OrtSession s = env.createSession(model(file).getAbsolutePath(),opt);
        line("OPEN "+provider+" "+file+" "+ms(t)+"ms inputs="+s.getInputNames()+" outputs="+s.getOutputNames());
        return s;
    }
    private void bench(OrtSession session, Map<String,OnnxTensor> input,String label) throws Exception {
        for(int i=0;i<3;i++) {
           long t=now();
           try(OrtSession.Result out=session.run(input)){
               Object value=out.get(0).getValue();
               assertNotNull(value);
               line("RUN "+label+" "+i+" "+ms(t)+"ms output="+out.get(0).getInfo());
           }
        }
    }
    @Test public void contentVecRealWeights() throws Exception {
       try(OrtEnvironment env=OrtEnvironment.getEnvironment();
           OrtSession sess=open(env,"contentvec_768l12_q8.onnx")){
          int n=32000; // 2s of 16k mono
          float[] samples=new float[n]; long[] mask=new long[n];
          for(int i=0;i<n;i++){ samples[i]=(float)(0.08*Math.sin(2.0*Math.PI*200*i/16000)); mask[i]=1L;}
          try(OnnxTensor audio=OnnxTensor.createTensor(env,FloatBuffer.wrap(samples),new long[]{1,n});
              OnnxTensor attention=OnnxTensor.createTensor(env,LongBuffer.wrap(mask),new long[]{1,n})){
             Map<String,OnnxTensor> feeds=new HashMap<>();
             feeds.put("input_values",audio);feeds.put("attention_mask",attention);
             bench(sess,feeds,"ContentVec_q8_2s");
          }
       }
    }
    @Test public void rmvpeRealWeights() throws Exception {
       try(OrtEnvironment env=OrtEnvironment.getEnvironment();
           OrtSession sess=open(env,"rmvpe_q8.onnx")){
          int frames=128;
          float[] mel=new float[128*frames];
          for(int i=0;i<mel.length;i++) mel[i]=(float)(0.05*Math.sin(i*0.1));
          try(OnnxTensor input=OnnxTensor.createTensor(env,FloatBuffer.wrap(mel),new long[]{1,128,frames})){
              bench(sess,Map.of("input",input),"RMVPE_q8_128frames");
          }
       }
    }
    @Test public void synthRealWeights() throws Exception { synthBenchmark("cpu"); }
    @Test public void synthXnnpack() throws Exception { synthBenchmark("xnnpack"); }
    @Test public void synthNnapi() throws Exception { synthBenchmark("nnapi"); }
    @Test public void synthWebGpu() throws Exception { synthBenchmark("webgpu"); }
    private void synthBenchmark(String provider) throws Exception {
       try(OrtEnvironment env=OrtEnvironment.getEnvironment();
           OrtSession sess=open(env,"Nova_HD.onnx",provider)){
          int frames=96;
          float[] feat=new float[frames*768];
          for(int i=0;i<feat.length;i++) feat[i]=(float)(0.03*Math.sin(i*0.04));
          long[] coarse = new long[frames];float[] fine=new float[frames];
          for(int i=0;i<frames;i++){coarse[i]=104L;fine[i]=220.0f;}
          try(OnnxTensor features=OnnxTensor.createTensor(env,FloatBuffer.wrap(feat),new long[]{1,frames,768});
              OnnxTensor plen=OnnxTensor.createTensor(env,LongBuffer.wrap(new long[]{frames}),new long[]{1});
              OnnxTensor pitch=OnnxTensor.createTensor(env,LongBuffer.wrap(coarse),new long[]{1,frames});
              OnnxTensor pitchf=OnnxTensor.createTensor(env,FloatBuffer.wrap(fine),new long[]{1,frames});
              OnnxTensor sid=OnnxTensor.createTensor(env,LongBuffer.wrap(new long[]{0}),new long[]{1})){
             Map<String,OnnxTensor> feeds=new HashMap<>();
             feeds.put("feats",features);feeds.put("p_len",plen); feeds.put("pitch",pitch);
             feeds.put("pitchf",pitchf);feeds.put("sid",sid);
             bench(sess,feeds,"NovaHD_"+provider+"_96frames");
          }
       }
    }
}
