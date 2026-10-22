package org.mushroomrecompiled;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Native, scrollable menus with one shared visual language and real actions. */
final class MenuUi {
    static final int BACKGROUND=0xff17191f,PANEL=0xff24272f,INK=0xfff3eee5,MUTED=0xffadb3c0,RED=0xffe45858;
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static AlertDialog.Builder builder(Activity a){return new AlertDialog.Builder(a,R.style.GameDialogTheme);}
    static GradientDrawable background(Context c,int color,int border,float radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
        if(border!=0)d.setStroke(dp(c,1),border);return d;
    }
    static TextView text(Context c,String value,int size,int color,boolean bold){
        TextView v=new TextView(c);v.setText(value);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);v.setTextColor(color);
        v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return v;
    }
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static AlertDialog menu(Activity a,String title,String subtitle,String[] items,String[] descriptions,boolean grid,int primary,IntConsumer action,Runnable back,boolean showBack){
        LinearLayout box=column(a);box.setPadding(dp(a,20),dp(a,16),dp(a,20),dp(a,12));
        LinearLayout heading=new LinearLayout(a);heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark=new ImageView(a);mark.setImageResource(R.drawable.ic_mushroom);mark.setContentDescription("Mushroom Recompiled");
        heading.addView(mark,new LinearLayout.LayoutParams(dp(a,40),dp(a,40)));
        LinearLayout titles=column(a);titles.setPadding(dp(a,12),0,dp(a,8),0);
        titles.addView(text(a,title,grid?23:21,INK,true));
        if(!subtitle.isEmpty()){TextView sub=text(a,subtitle,12,MUTED,false);sub.setPadding(0,dp(a,3),0,0);titles.addView(sub);}
        heading.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        final AlertDialog[] holder=new AlertDialog[1];
        if(showBack){Button close=new Button(a);close.setText("Volver");close.setTextSize(12);close.setTextColor(INK);close.setMinWidth(0);close.setMinimumWidth(0);close.setBackground(background(a,PANEL,0xff414651,9));close.setOnClickListener(v->{holder[0].dismiss();back.run();});heading.addView(close,new LinearLayout.LayoutParams(dp(a,72),dp(a,40)));}
        box.addView(heading);
        View rule=new View(a);rule.setBackgroundColor(0xff373b45);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,1));line.setMargins(0,dp(a,13),0,dp(a,10));box.addView(rule,line);
        int maxHeight=Math.max(dp(a,110),(int)(a.getResources().getDisplayMetrics().heightPixels*.82f)-dp(a,105));
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(maxHeight,MeasureSpec.AT_MOST));}};
        scroll.setClipToPadding(false);scroll.setFillViewport(false);LinearLayout rows=column(a);
        for(int i=0;i<items.length;){
            LinearLayout row=new LinearLayout(a);
            for(int col=0;col<(grid?2:1)&&i<items.length;col++,i++){
                final int index=i;String titleText=items[i],detail=descriptions==null?"":descriptions[i];
                if(!grid){int split=titleText.indexOf(" · ");if(split>=0){detail=titleText.substring(split+3);titleText=titleText.substring(0,split);}}
                LinearLayout card=column(a);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(a,14),dp(a,9),dp(a,14),dp(a,9));
                int color=i==primary?0xff9b303c:PANEL;
                card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),background(a,color,i==primary?RED:0xff343944,11),null));
                TextView label=text(a,titleText,grid?15:16,INK,true);label.setMaxLines(2);card.addView(label);
                if(!detail.isEmpty()){TextView sub=text(a,detail,12,i==primary?0xfff9d6d3:MUTED,false);sub.setPadding(0,dp(a,3),0,0);card.addView(sub);}
                card.setMinimumHeight(dp(a,grid?57:51));card.setClickable(true);card.setFocusable(true);
                card.setContentDescription(titleText+(detail.isEmpty()?"":", "+detail));
                card.setOnClickListener(v->{holder[0].setOnCancelListener(null);holder[0].dismiss();action.accept(index);});
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1);p.setMargins(col==0?0:dp(a,5),0,col==0&&grid?dp(a,5):0,dp(a,8));row.addView(card,p);
            }
            if(grid&&row.getChildCount()==1)row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
            rows.addView(row);
        }
        scroll.addView(rows);box.addView(scroll);
        if(grid){TextView footer=text(a,"MUSHROOM RECOMPILED  /  1.5.0",10,MUTED,true);footer.setPadding(0,dp(a,8),0,0);box.addView(footer);}
        AlertDialog dialog=builder(a).setView(box).create();holder[0]=dialog;dialog.setOnCancelListener(d->back.run());return dialog;
    }
    static void show(Activity a,AlertDialog dialog,boolean wide){
        dialog.show();Window w=dialog.getWindow();if(w==null)return;
        w.setBackgroundDrawable(background(a,BACKGROUND,0xff393d48,18));
        int pixels=a.getResources().getDisplayMetrics().widthPixels;
        w.setLayout(Math.min(dp(a,wide?780:620),(int)(pixels*.88f)),ViewGroup.LayoutParams.WRAP_CONTENT);
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.72f);
    }
}
