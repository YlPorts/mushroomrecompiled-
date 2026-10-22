package org.mushroomrecompiled;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GameActivity extends SDLActivity implements TouchControlsView.Listener {
    private static final int PICK_ROM=101;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private Settings settings;
    private TouchControlsView touch;
    private AlertDialog dialog;
    private boolean menu=true,editing,loading,destroyed,initial=true,resumeAfterLoad;
    private LinearLayout editorBar;
    private SeekBar sizeSlider;
    private String selectedRom="";
    private int safeLeft,safeTop,safeRight;
    private final String[] actionNames={"B · correr / habilidad","A · saltar / volar","SELECT","START","Arriba","Abajo","Izquierda","Derecha","Turbo B","Turbo A","Menú"};

    @Override protected String[] getLibraries(){return new String[]{"SDL2","main"};}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(mLayout==null)return; // SDL already presents its library-load error dialog.
        NativeBridge.beginSession();
        preferences=getSharedPreferences("mushroom-android-v1",MODE_PRIVATE);settings=new Settings(preferences);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        touch=new TouchControlsView(this,settings,this);
        mLayout.addView(touch,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        touch.setOnApplyWindowInsetsListener((v,insets)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){
                Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
                Insets system=insets.getInsets(WindowInsets.Type.systemBars());
                l=Math.max(bars.left,system.left);t=Math.max(bars.top,system.top);r=Math.max(bars.right,system.right);b=Math.max(bars.bottom,system.bottom);
            }else{
                l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
                    l=Math.max(l,insets.getDisplayCutout().getSafeInsetLeft());t=Math.max(t,insets.getDisplayCutout().getSafeInsetTop());
                    r=Math.max(r,insets.getDisplayCutout().getSafeInsetRight());b=Math.max(b,insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            safeLeft=l;safeTop=t;safeRight=r;
            touch.safeArea(l,t,r,b);NativeBridge.safeArea(l,t,r,b,touch.getWidth(),touch.getHeight());
            if(editorBar!=null)editorBar.setPadding(16+l,4+t,16+r,4);return insets;
        });
        NativeBridge.setPaused(true);settings.apply();immersive();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::back);
        handler.post(poll);
        handler.post(this::showMain);
        String uri=preferences.getString("romUri","");
        if(!uri.isEmpty())importRom(Uri.parse(uri),true,false);
    }
    private void immersive(){
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        }else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override protected void onPause(){
        if(touch!=null){touch.cancel();NativeBridge.setPaused(true);menu=true;}
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();immersive();
        if(settings!=null&&!initial){NativeBridge.setPaused(true);handler.post(()->{if(!loading&&!editing)showMain();});if(!loading&&!selectedRom.isEmpty())verifyRememberedAccess();}
        initial=false;
    }
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(touch!=null)touch.cancel();io.shutdownNow();super.onDestroy();}
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ uses the callback registered in onCreate.
    @Override public void onBackPressed(){back();}
    private void back(){if(editing){finishEditor();return;}if(dialog!=null){showMain();return;}menu();}
    @Override public void menu(){if(editing){finishEditor();return;}pause();showMain();}
    private void pause(){menu=true;touch.cancel();NativeBridge.setPaused(true);touch.mode(false,false);}
    private boolean loaded(){return (NativeBridge.state()&NativeBridge.LOADED)!=0;}
    private void resume(){
        if(!loaded()){toast("Selecciona primero la ROM compatible.");showMain();return;}
        dismiss();menu=false;touch.cancel();updateTouchMode();NativeBridge.setPaused(false);immersive();
    }
    private void dismiss(){if(dialog!=null){dialog.setOnCancelListener(null);dialog.dismiss();dialog=null;}}
    private void list(String title,String[] items,java.util.function.IntConsumer action,Runnable back){
        pause();dismiss();
        dialog=new AlertDialog.Builder(this).setTitle(title).setItems(items,(d,n)->{dialog=null;action.accept(n);})
            .setNegativeButton("Atrás",(d,n)->{dialog=null;back.run();}).create();
        dialog.setOnCancelListener(d->{dialog=null;back.run();});dialog.show();
    }
    private void showMain(){
        if(destroyed||editing)return;
        pause();dismiss();
        final boolean ready=loaded();
        String[] items=ready?new String[]{"Continuar","Reiniciar","Guardar estado · slot "+settings.slot,"Cargar estado · slot "+settings.slot,"Controles","Vídeo","Audio","Cambiar ROM","Información del proyecto","Salir"}
            :new String[]{"Seleccionar ROM","Iniciar juego","Configuración","Información del proyecto","Salir"};
        dialog=new AlertDialog.Builder(this).setTitle("🍄 Mushroom Recompiled · 1.5.0")
            .setItems(items,(d,n)->{dialog=null;
                if(ready){
                    switch(n){case 0:resume();break;case 1:restart();break;case 2:slots(true);break;case 3:slots(false);break;case 4:controls();break;case 5:video();break;case 6:audio();break;case 7:pickRom();break;case 8:about();break;case 9:quit();break;}
                }else switch(n){case 0:pickRom();break;case 1:if(loading){resumeAfterLoad=true;toast("Validando ROM…");showMain();}else if(!selectedRom.isEmpty()){resumeAfterLoad=true;send(NativeBridge.LOAD_ROM,selectedRom,0);}else{toast("Selecciona la ROM para iniciar.");pickRom();}break;case 2:configuration();break;case 3:about();break;case 4:quit();break;}
            }).create();
        dialog.setOnCancelListener(d->{dialog=null;if(loaded())resume();else showMain();});dialog.show();
    }
    private void configuration(){list("Configuración",new String[]{"Controles","Vídeo","Audio"},n->{if(n==0)controls();else if(n==1)video();else audio();},this::showMain);}
    private void restart(){pause();dismiss();dialog=new AlertDialog.Builder(this).setTitle("Reiniciar partida")
        .setMessage("Se perderá el progreso desde el último estado guardado.")
        .setPositiveButton("Reiniciar",(d,n)->{send(NativeBridge.RESET,"",0);resume();})
        .setNegativeButton("Cancelar",(d,n)->showMain()).create();dialog.setOnCancelListener(d->showMain());dialog.show();}
    private void quit(){pause();dismiss();send(NativeBridge.QUIT,"",0);destroyed=true;handler.removeCallbacksAndMessages(null);io.shutdownNow();finish();}
    private File slotFile(int n){return new File(getFilesDir(),"slot-"+n+".state");}
    private void slots(boolean save){
        String[] items=new String[5];
        for(int i=0;i<5;i++)items[i]="Slot "+(i+1)+(slotFile(i+1).exists()?" · guardado":" · vacío");
        list(save?"Guardar estado":"Cargar estado",items,n->{
            settings.slot=n+1;settings.save();
            if(!save&&!slotFile(n+1).exists()){toast("Este slot está vacío.");slots(false);return;}
            if(save&&slotFile(n+1).exists()){
                dialog=new AlertDialog.Builder(this).setTitle("Sobrescribir slot "+(n+1)+"?")
                    .setPositiveButton("Guardar",(d,b)->{send(NativeBridge.SAVE,slotFile(n+1).getAbsolutePath(),n+1);showMain();})
                    .setNegativeButton("Cancelar",(d,b)->slots(true)).create();dialog.setOnCancelListener(d->slots(true));dialog.show();
            }else{send(save?NativeBridge.SAVE:NativeBridge.LOAD_STATE,slotFile(n+1).getAbsolutePath(),n+1);showMain();}
        },this::showMain);
    }
    private void pickRom(){
        pause();dismiss();
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/octet-stream","application/x-nes-rom","application/x-nes","application/vnd.nintendo.snes.rom","*/*"});
        try{startActivityForResult(intent,PICK_ROM);}catch(android.content.ActivityNotFoundException e){toast("No hay selector de documentos disponible.");showMain();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_ROM)return;
        if(result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException e){toast("El proveedor no permite conservar el acceso; puede ser necesario seleccionar la ROM otra vez.");}
            importRom(uri,false,false);
        }else showMain();
    }
    private void importRom(Uri uri,boolean restore,boolean autoStart){
        loading=true;resumeAfterLoad=autoStart;pause();
        io.execute(()->{
            try{
                String name="";
                try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
                }
                if(name!=null&&!name.isEmpty()&&!name.toLowerCase(java.util.Locale.ROOT).endsWith(".nes"))throw new java.io.IOException("Selecciona el archivo .nes sin comprimir, no un ZIP.");
                byte[] rom;
                try(InputStream stream=getContentResolver().openInputStream(uri)){rom=RomValidator.read(stream);}
                File target=new File(getFilesDir(),"active-rom.nes"),temp=new File(getFilesDir(),"active-rom.nes.tmp");
                try(FileOutputStream output=new FileOutputStream(temp)){output.write(rom);output.getFD().sync();}
                if(!temp.renameTo(target))throw new java.io.IOException("No se pudo preparar la ROM en el almacenamiento privado.");
                String old=preferences.getString("romUri","");preferences.edit().putString("romUri",uri.toString()).apply();
                if(!old.isEmpty()&&!old.equals(uri.toString()))try{getContentResolver().releasePersistableUriPermission(Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
                handler.post(()->{if(destroyed)return;selectedRom=target.getAbsolutePath();loading=false;send(NativeBridge.LOAD_ROM,selectedRom,0);});
            }catch(Exception e){
                if(!restore&&!uri.toString().equals(preferences.getString("romUri","")))try{getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
                handler.post(()->{
                    if(destroyed)return;loading=false;resumeAfterLoad=false;
                    if(restore){selectedRom="";preferences.edit().remove("romUri").apply();}
                    dismiss();dialog=new AlertDialog.Builder(this).setTitle(restore?"Selecciona la ROM nuevamente":"ROM no compatible")
                        .setMessage((restore?"El archivo recordado no está disponible o no es compatible.\n\n":"")+e.getMessage())
                        .setPositiveButton("Seleccionar ROM",(d,n)->pickRom()).setNegativeButton("Volver",(d,n)->showMain()).create();
                    dialog.setOnCancelListener(d->showMain());dialog.show();
                });
            }
        });
    }
    private void verifyRememberedAccess(){
        String remembered=preferences.getString("romUri","");if(remembered.isEmpty())return;
        io.execute(()->{
            try(InputStream stream=getContentResolver().openInputStream(Uri.parse(remembered))){RomValidator.read(stream);}
            catch(Exception e){handler.post(()->{
                if(destroyed||!remembered.equals(preferences.getString("romUri","")))return;
                pause();dismiss();dialog=new AlertDialog.Builder(this).setTitle("ROM no disponible")
                    .setMessage("El documento recordado ha desaparecido, cambió o perdió el permiso. Selecciónalo nuevamente para conservar el acceso.")
                    .setPositiveButton("Seleccionar ROM",(d,n)->pickRom()).setNegativeButton("Volver",(d,n)->showMain()).create();
                dialog.setOnCancelListener(d->showMain());dialog.show();
            });}
        });
    }
    private void choice(String title,String[] options,int current,java.util.function.IntConsumer selected,Runnable back){
        pause();dismiss();dialog=new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options,current,(d,n)->{selected.accept(n);settings.apply();back.run();})
            .setNegativeButton("Atrás",(d,n)->back.run()).create();dialog.setOnCancelListener(d->back.run());dialog.show();
    }
    private void slider(String title,int min,int max,int value,java.util.function.IntConsumer selected,Runnable back){
        pause();dismiss();LinearLayout box=column();TextView text=label(title+": "+value);SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(value-min);box.addView(text);box.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean fromUser){text.setText(title+": "+(n+min));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Aplicar",(d,n)->{selected.accept(bar.getProgress()+min);settings.apply();touch.changed();back.run();})
            .setNegativeButton("Cancelar",(d,n)->back.run()).create();dialog.setOnCancelListener(d->back.run());dialog.show();
    }
    private void video(){
        String[] ratios={"4:3 original","16:9 ampliado"},scales={"Ajustar con bandas","Escalado entero","Pixel perfect (píxeles cuadrados)"};
        list("Vídeo",new String[]{"Formato · "+ratios[settings.videoAudio[0]],"Escala · "+scales[settings.videoAudio[1]],"Filtro · "+(settings.videoAudio[2]==0?"Nítido":"Suave"),"Brillo · "+settings.videoAudio[7]+"%","Scanlines · "+settings.videoAudio[8]+"%"},n->{
            switch(n){case 0:choice("Formato",ratios,settings.videoAudio[0],v->settings.videoAudio[0]=v,this::video);break;
            case 1:choice("Escala",scales,settings.videoAudio[1],v->settings.videoAudio[1]=v,this::video);break;
            case 2:choice("Filtro",new String[]{"Nítido","Suave"},settings.videoAudio[2],v->settings.videoAudio[2]=v,this::video);break;
            case 3:slider("Brillo (%)",50,150,settings.videoAudio[7],v->settings.videoAudio[7]=v,this::video);break;
            case 4:slider("Scanlines (%)",0,75,settings.videoAudio[8],v->settings.videoAudio[8]=v,this::video);break;}
        },this::showMain);
    }
    private void audio(){
        list("Audio NES",new String[]{"Volumen · "+settings.videoAudio[3]+"%","Silenciar · "+(settings.videoAudio[9]!=0?"sí":"no"),"Frecuencia · "+settings.videoAudio[4]+" Hz","Canales · "+(settings.videoAudio[5]!=0?"Estéreo":"Mono"),"Reserva · "+settings.videoAudio[6]+" ms"},n->{
            switch(n){case 0:slider("Volumen (%)",0,100,settings.videoAudio[3],v->settings.videoAudio[3]=v,this::audio);break;
            case 1:settings.videoAudio[9]^=1;settings.apply();audio();break;
            case 2:choice("Frecuencia",new String[]{"44100 Hz","48000 Hz","96000 Hz"},settings.videoAudio[4]==44100?0:settings.videoAudio[4]==48000?1:2,v->settings.videoAudio[4]=new int[]{44100,48000,96000}[v],this::audio);break;
            case 3:choice("Canales",new String[]{"Mono","Estéreo (NES dual mono)"},settings.videoAudio[5],v->settings.videoAudio[5]=v,this::audio);break;
            case 4:choice("Reserva de audio",new String[]{"20 ms","40 ms","60 ms","80 ms"},settings.videoAudio[6]/20-1,v->settings.videoAudio[6]=(v+1)*20,this::audio);break;}
        },this::showMain);
    }
    private void controls(){
        list("Controles",new String[]{"Editar posición y tamaño","Opacidad · "+settings.opacity+"%","Ocultar al conectar mando · "+(settings.hideWithController?"sí":"no"),"Reasignar mando SDL","Zona muerta · "+settings.deadzone+"%","Restaurar controles","Acceso a mandos Bluetooth HID","Seleccionar mando","Reservar franja inferior · "+(settings.reserveControls?"sí":"no")},n->{
            switch(n){case 0:startEditor();break;case 1:slider("Opacidad (%)",10,100,settings.opacity,v->settings.opacity=v,this::controls);break;
            case 2:settings.hideWithController=!settings.hideWithController;settings.save();controls();break;case 3:bindings();break;
            case 4:slider("Zona muerta (%)",5,80,settings.deadzone,v->settings.deadzone=v,this::controls);break;
            case 5:float[] x={.12f,.86f,.94f,.46f,.54f,.95f},y={.73f,.79f,.63f,.96f,.96f,.08f};System.arraycopy(x,0,settings.x,0,6);System.arraycopy(y,0,settings.y,0,6);Arrays.fill(settings.size,1f);Arrays.fill(settings.buttons,-1);settings.opacity=50;settings.deadzone=25;settings.device="";settings.reserveControls=true;settings.hideWithController=true;settings.apply();touch.changed();controls();break;
            case 6:if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},102);}else{toast("Acceso disponible. Empareja tu mando desde los ajustes Bluetooth de Android.");controls();}break;
            case 7:controllerDevices();break;case 8:settings.reserveControls=!settings.reserveControls;settings.save();controls();break;}
        },this::showMain);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==102){
            if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){if(mHIDDeviceManager!=null)mHIDDeviceManager.initialize(false,true);toast("Acceso Bluetooth HID habilitado.");}
            else toast("Los mandos reconocidos como dispositivos de entrada Android siguen disponibles.");
            controls();
        }
    }
    private void controllerDevices(){
        String list=NativeBridge.controllers();String[] rows=list==null||list.isEmpty()?new String[0]:list.split("\n");
        String[] names=new String[rows.length+1],keys=new String[rows.length+1];names[0]="Automático";keys[0]="";int selected=0;
        for(int i=0;i<rows.length;i++){String[] fields=rows[i].split("\t",2);keys[i+1]=fields[0];names[i+1]=fields.length>1?fields[1]:"Mando";if(settings.device.equals(keys[i+1]))selected=i+1;}
        choice("Seleccionar mando SDL",names,selected,n->settings.device=keys[n],this::controls);
    }
    private void bindings(){
        String[] names=new String[12];
        for(int i=0;i<11;i++)names[i]=actionNames[i]+" · "+(settings.buttons[i]<0?"automático":"botón "+settings.buttons[i]);names[11]="Restaurar asignaciones";
        list("Mando "+((NativeBridge.state()&NativeBridge.CONTROLLER)!=0?"conectado":"desconectado"),names,n->{
            if(n==11){Arrays.fill(settings.buttons,-1);settings.apply();bindings();return;}
            if((NativeBridge.state()&NativeBridge.CONTROLLER)==0){toast("Conecta un mando Bluetooth o USB para reasignar.");bindings();return;}
            dismiss();dialog=new AlertDialog.Builder(this).setTitle(actionNames[n]).setMessage("Suelta los botones y pulsa el botón físico que quieras asignar.")
                .setNeutralButton("Automático",(d,b)->{send(NativeBridge.CAPTURE,"",-1);settings.buttons[n]=-1;settings.apply();bindings();})
                .setNegativeButton("Cancelar",(d,b)->{send(NativeBridge.CAPTURE,"",-1);bindings();}).create();
            dialog.setOnCancelListener(d->{send(NativeBridge.CAPTURE,"",-1);bindings();});dialog.show();send(NativeBridge.CAPTURE,"",n);
        },this::controls);
    }
    private void startEditor(){
        pause();dismiss();editing=true;touch.edit(true);
        editorBar=new LinearLayout(this);editorBar.setGravity(Gravity.CENTER);editorBar.setPadding(16+safeLeft,4+safeTop,16+safeRight,4);editorBar.setBackgroundColor(0xcc101116);
        TextView label=label("Tamaño");editorBar.addView(label);
        sizeSlider=new SeekBar(this);sizeSlider.setMax(150);sizeSlider.setProgress(50);editorBar.addView(sizeSlider,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user)touch.resizeSelected(.5f+p/100f);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        Button done=new Button(this);done.setText("Guardar");done.setOnClickListener(v->finishEditor());editorBar.addView(done);
        android.widget.RelativeLayout.LayoutParams params=new android.widget.RelativeLayout.LayoutParams(Math.round(touch.getWidth()*.6f),ViewGroup.LayoutParams.WRAP_CONTENT);params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);mLayout.addView(editorBar,params);
    }
    @Override public void edited(int selected){if(sizeSlider!=null)sizeSlider.setProgress(Math.round((settings.size[selected]-.5f)*100));}
    private void finishEditor(){editing=false;touch.edit(false);settings.save();if(editorBar!=null){mLayout.removeView(editorBar);editorBar=null;}controls();}
    private void about(){
        list("Mushroom Recompiled · Proyecto",new String[]{"Acerca del port","Licencias y créditos","Diagnóstico de ejecución"},n->{
            if(n==0)message("Acerca del proyecto","Mushroom Recompiled 1.5.0\nPort Android ARM64 del núcleo NESRecomp por ciclos.\n\nJuego no oficial. Nintendo conserva los derechos de Super Mario Bros. 3. ROM aportada por el usuario, sin modificar.\n\n16:9 usa el compositor y los enemigos panorámicos de PC; las escenas no compatibles mantienen su encuadre. 19:9 no está disponible.\n\nProyecto original y port de PC: Adoregabriel2005 / Underplay.\nFork Android: YlPorts.\nNESRecomp: Matthew Stanley.\nBeta testers: ALEISTECH y RETROCESSO.",this::about);
            else if(n==1)licenses();else message("Diagnóstico",NativeBridge.diagnostics(),this::about);
        },this::showMain);
    }
    private void licenses(){
        try{String[] files=getAssets().list("licenses");if(files==null)return;list("Licencias incluidas",files,n->{
            try(InputStream stream=getAssets().open("licenses/"+files[n])){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[8192];int count;while((count=stream.read(chunk))!=-1)bytes.write(chunk,0,count);message(files[n],bytes.toString(StandardCharsets.UTF_8.name()),this::licenses);}
            catch(Exception e){toast(e.getMessage());licenses();}
        },this::about);}catch(Exception e){toast(e.getMessage());about();}
    }
    private void message(String title,String text,Runnable back){
        pause();dismiss();ScrollView scroll=new ScrollView(this);LinearLayout box=column();box.addView(label(text));
        if(title.equals("Acerca del proyecto"))try(InputStream stream=getAssets().open("underplay.png")){
            android.widget.ImageView image=new android.widget.ImageView(this);image.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(stream,"Underplay"));image.setAdjustViewBounds(true);image.setContentDescription("Powered by Underplay");box.addView(image);
        }catch(java.io.IOException e){toast("No se pudo abrir la identidad Underplay.");}
        scroll.addView(box);
        dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Volver",(d,n)->back.run()).create();dialog.setOnCancelListener(d->back.run());dialog.show();
    }
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(28,16,28,16);return box;}
    private TextView label(String text){TextView label=new TextView(this);label.setText(text);label.setTextSize(16);label.setPadding(8,8,8,8);return label;}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void send(int operation,String path,int slot){if(!NativeBridge.command(operation,path,slot))toast("No se pudo enviar la operación al juego. Vuelve a intentarlo.");}
    private void updateTouchMode(){
        boolean controller=(NativeBridge.state()&NativeBridge.CONTROLLER)!=0;
        touch.mode(!menu,controller);
        NativeBridge.setControlGutter(!menu&&settings.reserveControls&&!(controller&&settings.hideWithController)?16:0);
    }
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(destroyed)return;
        String result;
        while(!(result=NativeBridge.result()).isEmpty()){
            if(result.equals("@menu")){menu();continue;}
            if(result.startsWith("@binding:")){
                String[] values=result.split(":");int action=Integer.parseInt(values[1]),button=Integer.parseInt(values[2]);
                if(action>=0&&action<11){settings.buttons[action]=button;settings.apply();bindings();}continue;
            }
            toast(result);
            if(result.startsWith("ROM cargada")){if(resumeAfterLoad){resumeAfterLoad=false;resume();}else if(menu)showMain();}
        }
        updateTouchMode();
        handler.postDelayed(this,100);
    }};
}
