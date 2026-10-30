package com.zikrtime.widgetprobe;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;
import java.io.FileOutputStream;
/** Read a changing clock without waiting for accessibility-idle. Never touches app data or UI. */
public class DumpInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityNodeInfo root=null;
            for(int i=0;i<40 && root==null;i++) { root=automation.getRootInActiveWindow(); if(root==null)SystemClock.sleep(100); }
            if(root==null)throw new IllegalStateException("No active accessibility window");
            FileOutputStream file=new FileOutputStream(getTargetContext().getFilesDir()+"/window.xml");
            XmlSerializer xml=Xml.newSerializer();xml.setOutput(file,"UTF-8");xml.startDocument("UTF-8",true);xml.startTag(null,"hierarchy");
            node(xml,root,0);xml.endTag(null,"hierarchy");xml.endDocument();file.close();root.recycle();
            result.putString("stream","snapshot saved");finish(-1,result);
        }catch(Exception e){result.putString("stream",e.toString());finish(1,result);}
    }
    private static String s(CharSequence text){return text==null?"":text.toString();}
    private static void node(XmlSerializer x,AccessibilityNodeInfo n,int depth)throws Exception {
        if(depth>60)return;
        Rect b=new Rect();n.getBoundsInScreen(b);
        x.startTag(null,"node");
        x.attribute(null,"text",s(n.getText()));x.attribute(null,"resource-id",s(n.getViewIdResourceName()));
        x.attribute(null,"class",s(n.getClassName()));x.attribute(null,"package",s(n.getPackageName()));
        x.attribute(null,"content-desc",s(n.getContentDescription()));
        x.attribute(null,"clickable",String.valueOf(n.isClickable()));x.attribute(null,"scrollable",String.valueOf(n.isScrollable()));
        x.attribute(null,"enabled",String.valueOf(n.isEnabled()));x.attribute(null,"selected",String.valueOf(n.isSelected()));
        x.attribute(null,"bounds","["+b.left+","+b.top+"]["+b.right+","+b.bottom+"]");
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null){if(child.isVisibleToUser())node(x,child,depth+1);child.recycle();}}
        x.endTag(null,"node");
    }
}
