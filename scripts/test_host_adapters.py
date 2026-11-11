#!/usr/bin/env python3
"""JVM regression tests with renamed host shapes; no device/account is required.

Mocks provide the host/framework boundary; NetworkHook, Shape, HostEventHook and
PlayerControlBridge are compiled from the actual shipping sources.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

p = argparse.ArgumentParser()
p.add_argument("--jdk", type=Path, required=True)
args = p.parse_args()
root = Path(__file__).resolve().parents[1]
base = root / "dlsitefloat-overlay/app/src/main/java"
pkg = "io.github.ariinyume.dlsitesoundfloat"
stubs = {
"android.os.Looper": 'public class Looper { public static Looper getMainLooper(){return new Looper();} }',
"android.os.Handler": 'public class Handler { public Handler(Looper l){} public boolean postDelayed(Runnable r,long t){return true;} }',
"io.github.libxposed.api.XposedInterface": 'public interface XposedInterface { interface Chain { Object getThisObject(); Object getArg(int i); } }',
pkg+".util.XposedCompat": '''
import java.lang.reflect.*; import java.util.*; import io.github.libxposed.api.XposedInterface;
public class XposedCompat {
 public static final Map<Executable,SimpleHook> hooks=new HashMap<>();
 public abstract static class SimpleHook { protected abstract Object after(XposedInterface.Chain c,Object r) throws Throwable; }
 public static Class<?> findClass(String n,ClassLoader c) throws Exception { return Class.forName(n,false,c); }
 public static Method findMethodExact(Class<?> c,String n,Class<?>...p) throws Exception { return c.getDeclaredMethod(n,p); }
 public static void hookMethod(Executable e,SimpleHook h){hooks.put(e,h);}
 public static void log(String s){}
 public static Object callMethod(Object o,String n,Object... a) throws Exception {
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())
   if(m.getName().equals(n)&&m.getParameterCount()==a.length){m.setAccessible(true);return m.invoke(o,a);}
  throw new NoSuchMethodException(n);
 }
}''',
pkg+".util.NetLogFile": 'public class NetLogFile { public static void init(Object o){} public static void log(String s){} }',
pkg+".data.LocalLrcManager": '''public class LocalLrcManager {
 public static boolean override; public static int resets;
 public static boolean isOverrideActive(){return override;}
 public static void onTrackChanged(){resets++;} public static void maybeRestore(Object o){}
}''',
pkg+".data.SubtitleRepository": '''import java.util.*;
public class SubtitleRepository {
 public int loads,changes,state; public long position; public boolean playing; public String json;
 public Object getAppContext(){return null;} public List<Object> getCues(){return Collections.emptyList();}
 public void loadFromJson(String s){loads++;json=s;} public void onTrackChanged(String s){changes++;}
 public void setPlaybackPositionMs(long v){position=v;} public void setPlaying(boolean b){playing=b;}
 public void setPlaybackState(int s){state=s;}
}''',
pkg+".hook.ActivityButtonHook": 'public class ActivityButtonHook { public static Object currentActivity(){return null;} }',
"androidx.media3.exoplayer.ExoPlayer": 'public interface ExoPlayer {}',
"W2.I": '''public class I implements androidx.media3.exoplayer.ExoPlayer {
 public boolean ready; public long sought;
 public void V(boolean b){ready=b;} public void N(long n){sought=n;}
}''',
"expo.modules.audio.AudioSource": 'public class AudioSource { public String getUri(){return "https://audio/track.mp3?token=x";} }',
"expo.modules.audio.AudioPlaylist": '''import java.util.*;
public class AudioPlaylist {
 public Object z=new W2.I(); public List<AudioSource> sources=Arrays.asList(new AudioSource(),new AudioSource());
 public int next,previous; public Map<String,Object> value=new HashMap<>();
 public Map<String,Object> s(){return value;} public void l0(){next++;} public void p0(){previous++;}
}''',
"expo.modules.audio.AudioPlayer": 'import java.util.*; public class AudioPlayer { public Map<String,Object> y(){return new HashMap<>();} }',
"expo.modules.fetch.NativeResponse": 'public class NativeResponse { public void u0(Object call,wk.G response){} }',
"wk.G": 'public class G implements java.io.Closeable { public H h; public F e(){return new F();} public void close(){} }',
"wk.F": 'public class F { public H h; public G a(){return new G();} }',
"wk.T": 'public class T { public String toString(){return "application/json";} }',
"wk.H": '''public class H implements java.io.Closeable {
 public Lk.S source=new Lk.S(); public int dangerousReads;
 public long e(){return source.buffer.bytes.length;} public T f(){return new T();} public Lk.S k(){return source;}
 public byte[] c(){dangerousReads++;return source.buffer.bytes;} public String p(){dangerousReads++;return "";}
 public java.io.InputStream a(){dangerousReads++;return null;} public void close(){}
}''',
"Lk.S": 'public class S { public B buffer=new B(); public boolean L(long n){return true;} public B b(){return buffer;} }',
"Lk.B": '''public class B implements Cloneable {
 public byte[] bytes=new byte[0]; public int consumed;
 public Object clone(){B b=new B();b.bytes=bytes.clone();return b;}
 public byte[] n(){consumed++;return bytes;}
}''',
}
harness = r'''
import java.lang.reflect.*; import java.util.*; import java.io.*; import java.util.zip.*;
import io.github.ariinyume.dlsitesoundfloat.hook.*;
import io.github.ariinyume.dlsitesoundfloat.control.*;
import io.github.ariinyume.dlsitesoundfloat.data.*;
import io.github.ariinyume.dlsitesoundfloat.util.*;
public class AdapterTest {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Object call(Class<?> c,String n,Class<?>[] p,Object...a)throws Exception{Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(null,a);}
 static String peek(wk.H h)throws Exception{return (String)call(NetworkHook.class,"peekText",new Class[]{Object.class,long.class,long.class},h,h.e(),524288L);}
 static void status(Object player,Method getter,Map<?,?> value,SubtitleRepository r)throws Exception{
  call(HostEventHook.class,"consume",new Class[]{Object.class,Method.class,Map.class,SubtitleRepository.class},player,getter,value,r);
 }
 public static void main(String[] args)throws Exception{
  SubtitleRepository r=new SubtitleRepository();ClassLoader cl=AdapterTest.class.getClassLoader();
  NetworkHook.hook(cl,r);
  check(XposedCompat.hooks.containsKey(wk.F.class.getMethod("a")),"resolve actual renamed builder shape");
  check(call(NetworkHook.class,"resolveResponseClass",new Class[]{ClassLoader.class},cl)==wk.G.class,"response anchor");
  String json="{\"data\":{\"webvtt\":[{\"start_time\":\"00:00:00.000\",\"end_time\":\"00:00:02.000\",\"subtitles\":[\"字幕\"]}]}}";
  wk.H h=new wk.H();h.source.buffer.bytes=json.getBytes("UTF-8");
  check(json.equals(peek(h)),"unicode JSON peek");
  check(h.source.buffer.consumed==0&&h.dangerousReads==0,"peek preserves host bytes and stream");
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream g=new GZIPOutputStream(out)){g.write(json.getBytes("UTF-8"));}
  h.source.buffer.bytes=out.toByteArray();check(json.equals(peek(h)),"gzip JSON");
  h.source.buffer.bytes=new byte[]{0,1,2,3,4};check(peek(h)==null,"binary body ignored");
  h.source.buffer.bytes=new byte[524289];check(peek(h)==null,"oversized body ignored");
  Class<?>[] submitTypes={String.class,SubtitleRepository.class,String.class,String.class};
  call(NetworkHook.class,"submit",submitTypes,json,r,"test",null);
  check(r.loads==1&&json.equals(r.json),"subtitle delivered unchanged to repository");
  call(NetworkHook.class,"submit",submitTypes,"{\"title\":\"not subtitles\"}",r,"test",null);
  check(r.loads==1,"metadata cannot replace cues");
  LocalLrcManager.override=true;
  call(NetworkHook.class,"submit",submitTypes,json.replace("字幕","本地优先"),r,"test",null);
  check(r.loads==1,"local LRC takes priority");LocalLrcManager.override=false;
  HostEventHook.hook(cl,r);
  expo.modules.audio.AudioPlaylist playlist=new expo.modules.audio.AudioPlaylist();Method getter=playlist.getClass().getMethod("s");
  check(XposedCompat.hooks.containsKey(getter),"renamed status getter discovered");
  Map<String,Object> value=playlist.value;value.put("currentTime",1.25);value.put("duration",60.0);
  value.put("playing",true);value.put("isLoaded",true);value.put("trackCount",2);value.put("currentIndex",0);value.put("id","list");
  status(playlist,getter,value,r);
  check(r.position==1250&&r.playing&&r.state==3,"status seconds -> milliseconds and playing");
  check(r.changes==0,"first observation does not clear cues");
  check(PlayerControlBridge.getCurrentPositionMs()==1250&&PlayerControlBridge.getDurationMs()==60000,"controls use status clock");
  check("uri:https://audio/track.mp3".equals(PlayerControlBridge.getCurrentMediaKey()),"LRC cache uses stable source URI");
  PlayerControlBridge.togglePlayPause();check(!((W2.I)playlist.z).ready,"pause profile");
  PlayerControlBridge.seekTo(90000);check(((W2.I)playlist.z).sought==60000,"seek clamped to duration");
  PlayerControlBridge.next();PlayerControlBridge.previous();check(playlist.next==1&&playlist.previous==1,"track controls profile");
  status(playlist,getter,value,r);check(r.changes==0,"same track is a no-op");
  value.put("currentIndex",1);value.put("currentTime",0.5);status(playlist,getter,value,r);
  check(r.changes==1&&LocalLrcManager.resets==1&&r.position==500,"real track change resets override once");
  Map<String,Object> ghost=new HashMap<>();ghost.put("currentTime",0.0);ghost.put("playing",false);
  status(new expo.modules.audio.AudioPlayer(),getter,ghost,r);check(r.position==500&&r.changes==1,"ghost AudioPlayer cannot reset playlist");
  value.put("currentTime",Double.NaN);status(playlist,getter,value,r);check(r.position==500,"invalid clock rejected");
  System.out.println("PASS: "+checks+" adapter regression checks");
 }
}
'''
with tempfile.TemporaryDirectory(prefix="dlsite-adapter-tests-") as temp:
    work = Path(temp)
    sources = []
    for name, body in stubs.items():
        package, cls = name.rsplit(".", 1)
        f = work / (name.replace(".", "/") + ".java")
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(f"package {package};\n{body}", encoding="utf-8")
        sources.append(f)
    main = work / "AdapterTest.java"
    main.write_text(harness, encoding="utf-8")
    sources.append(main)
    for suffix in ("hook/NetworkHook.java", "hook/HostEventHook.java", "control/PlayerControlBridge.java", "util/Shape.java"):
        sources.append(base / pkg.replace(".", "/") / suffix)
    out = work / "classes"
    out.mkdir()
    exe = ".exe" if os.name == "nt" else ""
    subprocess.run([str(args.jdk / ("bin/javac" + exe)), "-encoding", "UTF-8", "-d", str(out), *map(str, sources)], check=True)
    subprocess.run([str(args.jdk / ("bin/java" + exe)), "-cp", str(out), "AdapterTest"], check=True)
