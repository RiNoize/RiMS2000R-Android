package org.rinoize.rims2000r;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Deliberately small diagnostic UI; the original synth firmware performs all synthesis. */
public final class MainActivity extends Activity {
    private static final int IMPORT_ZIP = 41;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService tasks = Executors.newSingleThreadExecutor();
    private File romDirectory;
    private UsbMidi midi;
    private AudioManager audio;
    private TextView firmware, diagnostics, midiStatus, programLabel, volumeLabel;
    private Button startButton, stopButton, importButton;
    private Spinner buffers, outputs, channels;
    private CheckBox separateDsp;
    private final List<Integer> outputIds = new ArrayList<>();
    private final List<View> playingControls = new ArrayList<>();
    private final Map<Integer, Integer> keyboardChannels = new HashMap<>();
    private int pending, program, previousState = -1;
    private boolean destroyed, resumed, firstAudioList = true;
    private String operation = "Importá el ZIP de firmware para comenzar.";
    private final AudioDeviceCallback audioCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { if (NativeBridge.state() == 0 && pending == 0) refreshOutputs(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { if (NativeBridge.state() == 0 && pending == 0) refreshOutputs(); }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (destroyed || !resumed) return;
            int state = NativeBridge.state();
            if (state == 2 && previousState != 2) sendProgram();
            previousState = state;
            diagnostics.setText(operation + "\n\n" + NativeBridge.stats());
            midiStatus.setText(midi.description());
            updateControls();
            ui.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { NativeBridge.state(); }
        catch (UnsatisfiedLinkError e) {
            new AlertDialog.Builder(this).setTitle("Biblioteca ARM64 no disponible").setMessage(e.toString()).setPositiveButton("Cerrar", (d,w) -> finish()).show();
            return;
        }
        romDirectory = new File(getFilesDir(), "ms2000");
        try { FirmwareImporter.recover(romDirectory); } catch (IOException e) { operation = e.getMessage(); }
        program = getPreferences(0).getInt("program", 0);
        audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        buildUi();
        midi = new UsbMidi(this);
        audio.registerAudioDeviceCallback(audioCallback, ui);
        refreshOutputs();
        updateControls();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(226,233,239));
        v.setPadding(dp(6), dp(4), dp(6), dp(4)); return v;
    }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private void equal(LinearLayout row, View v) { row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); }
    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(listener); return b;
    }
    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        return s;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.setBackgroundColor(Color.rgb(19,25,33)); scroll.addView(root); setContentView(scroll);
        LinearLayout title = row();
        TextView heading = text("RiMS2000R  ·  Alpha 0.1.0", 23); heading.setTypeface(null, Typeface.BOLD);
        title.addView(heading, new LinearLayout.LayoutParams(0,-2,1));
        title.addView(button("Acerca de", v -> about())); root.addView(title);
        firmware = text("", 14); root.addView(firmware);
        LinearLayout toolbar = row();
        importButton = button("Importar ROMs (ZIP)", v -> pickFirmware());
        startButton = button("Arrancar", v -> startEngine());
        stopButton = button("Detener", v -> stopEngine());
        equal(toolbar, importButton); equal(toolbar,startButton); equal(toolbar,stopButton);
        equal(toolbar, button("MIDI USB", v -> midi.choose())); root.addView(toolbar);

        LinearLayout config = row();
        LinearLayout bufferBox = new LinearLayout(this); bufferBox.setOrientation(LinearLayout.VERTICAL);
        bufferBox.addView(text("Cola de audio",13)); buffers = spinner(new String[]{"256 muestras", "512 muestras", "1024 muestras"});
        buffers.setSelection(getPreferences(0).getInt("bufferIndex",1)); bufferBox.addView(buffers); equal(config, bufferBox);
        LinearLayout audioBox = new LinearLayout(this); audioBox.setOrientation(LinearLayout.VERTICAL); audioBox.addView(text("Salida de audio",13));
        outputs = spinner(new String[]{"Salida del sistema"}); audioBox.addView(outputs);
        config.addView(audioBox, new LinearLayout.LayoutParams(0,-2,2));
        separateDsp = new CheckBox(this); separateDsp.setText("DSP en otro hilo\n(experimental)"); separateDsp.setTextSize(13);
        separateDsp.setChecked(getPreferences(0).getBoolean("dspThread",false)); equal(config,separateDsp); root.addView(config);

        LinearLayout performance = row();
        Button previous = button("− Programa", v -> changeProgram(-1)); playingControls.add(previous); equal(performance,previous);
        programLabel = text("",19); programLabel.setGravity(Gravity.CENTER); equal(performance,programLabel);
        Button next = button("Programa +", v -> changeProgram(1)); playingControls.add(next); equal(performance,next);
        performance.addView(text("Canal de prueba",13));
        String[] c = new String[16]; for (int i=0;i<16;i++) c[i]=Integer.toString(i+1);
        channels = spinner(c); channels.setSelection(getPreferences(0).getInt("channel",0)); performance.addView(channels);
        root.addView(performance); updateProgramLabel();
        LinearLayout gain = row(); volumeLabel = text("Volumen",14); gain.addView(volumeLabel);
        SeekBar volume = new SeekBar(this); volume.setMax(100); int initial = getPreferences(0).getInt("volume",50); volume.setProgress(initial);
        NativeBridge.volume(initial/100f); volumeLabel.setText("Volumen " + initial + "%");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek,int value,boolean fromUser) {
                NativeBridge.volume(value/100f); volumeLabel.setText("Volumen " + value + "%");
                if (fromUser) getPreferences(0).edit().putInt("volume",value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });
        gain.addView(volume,new LinearLayout.LayoutParams(0,-2,1));
        gain.addView(button("PANIC", v -> NativeBridge.panic())); root.addView(gain);

        LinearLayout notes = row();
        equal(notes,noteButton("Do",new int[]{60})); equal(notes,noteButton("Mi",new int[]{64}));
        equal(notes,noteButton("Sol",new int[]{67})); equal(notes,noteButton("Do agudo",new int[]{72}));
        equal(notes,noteButton("4 notas",new int[]{60,64,67,72})); root.addView(notes);
        root.addView(text("Mantené pulsadas las notas. CC, sustain y pitch bend del teclado pasan al firmware. Al salir de la app se detiene el audio.",12));
        midiStatus = text("MIDI USB desconectado",14); root.addView(midiStatus);
        diagnostics = text("",13); diagnostics.setTypeface(Typeface.MONOSPACE); diagnostics.setTextIsSelectable(true);
        diagnostics.setBackgroundColor(Color.rgb(11,17,23)); root.addView(diagnostics);
        LinearLayout tools = row();
        equal(tools,button("Reiniciar medidores", v -> NativeBridge.resetStats()));
        equal(tools,button("Copiar diagnóstico", v -> copyDiagnostic())); root.addView(tools);
    }
    private Button noteButton(String label, int[] notes) {
        Button b = button(label,null); final int[] heldChannel = {-1};
        b.setOnTouchListener((v,event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (NativeBridge.state()!=2) return true;
                    heldChannel[0] = channels.getSelectedItemPosition(); v.setPressed(true);
                    for (int note : notes) note(heldChannel[0],note,true); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (heldChannel[0]>=0) for (int note : notes) note(heldChannel[0],note,false);
                    heldChannel[0]=-1; v.setPressed(false); return true;
                default: return true;
            }
        });
        playingControls.add(b); return b;
    }
    private void note(int channel,int note,boolean down) {
        byte[] b={(byte)((down?0x90:0x80)|channel),(byte)note,(byte)(down?100:0)}; NativeBridge.midi(b,0,b.length);
    }
    private void changeProgram(int step) { program=(program+step+128)%128; updateProgramLabel(); sendProgram(); }
    private void updateProgramLabel() { programLabel.setText("ABCDEFGH".charAt(program/16)+String.format(Locale.ROOT,"%02d",program%16+1)); }
    private void sendProgram() {
        byte[] b={(byte)(0xc0|channels.getSelectedItemPosition()),(byte)program}; NativeBridge.midi(b,0,b.length);
        getPreferences(0).edit().putInt("program",program).putInt("channel",channels.getSelectedItemPosition()).apply();
    }
    private void refreshOutputs() {
        if (outputs==null) return;
        int old = outputs.getSelectedItemPosition(); int wanted = old>=0 && old<outputIds.size()?outputIds.get(old):0;
        outputIds.clear(); outputIds.add(0);
        List<String> labels = new ArrayList<>(); labels.add("Salida del sistema");
        int preferred = 0;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            String type;
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: type="Altavoz de la tablet"; if (preferred==0) preferred=device.getId(); break;
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET: type="Auriculares de la tablet"; preferred=device.getId(); break;
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET: type="USB"; break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: type="Bluetooth (alta latencia)"; break;
                default: continue;
            }
            outputIds.add(device.getId()); labels.add(type+" · "+device.getProductName());
        }
        if (firstAudioList) { wanted=preferred; firstAudioList=false; }
        outputs.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        int index=outputIds.indexOf(wanted); outputs.setSelection(Math.max(0,index));
    }
    private interface Job { void run() throws Exception; }
    private void task(String description, Job job) {
        if (destroyed || tasks.isShutdown()) return;
        pending++; operation=description; updateControls();
        tasks.execute(() -> {
            String failure=null;
            try { job.run(); } catch (Exception e) { failure=e.getMessage()==null?e.toString():e.getMessage(); }
            final String error=failure;
            ui.post(() -> {
                pending=Math.max(0,pending-1);
                if (destroyed) return;
                if (error!=null) { operation=error; if (resumed) new AlertDialog.Builder(this).setTitle("RiMS2000R").setMessage(error).setPositiveButton("Aceptar",null).show(); }
                else if (pending==0) operation="Operación completada. Versión experimental; comprobar audio y estabilidad.";
                updateControls();
            });
        });
    }
    private void startEngine() {
        if (!FirmwareImporter.ready(romDirectory)) { Toast.makeText(this,"Importá primero el ZIP de firmware.",Toast.LENGTH_LONG).show(); return; }
        final int frames = new int[]{256,512,1024}[buffers.getSelectedItemPosition()];
        int index=outputs.getSelectedItemPosition(); final int device=index>=0&&index<outputIds.size()?outputIds.get(index):0;
        final boolean dsp=separateDsp.isChecked(); previousState=-1;
        getPreferences(0).edit().putInt("bufferIndex",buffers.getSelectedItemPosition()).putBoolean("dspThread",dsp).apply();
        task("Iniciando emulación...", () -> {
            String error=NativeBridge.start(romDirectory.getAbsolutePath(),frames,device,dsp);
            if (error!=null&&!error.isEmpty()) throw new IOException(error);
        });
    }
    private void stopEngine() { keyboardChannels.clear(); task("Deteniendo y guardando la memoria del sintetizador...",NativeBridge::stop); }
    private void pickFirmware() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        startActivityForResult(intent,IMPORT_ZIP);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=IMPORT_ZIP||result!=RESULT_OK||data==null||data.getData()==null) return;
        final Uri uri=data.getData();
        task("Importando ROMs en el almacenamiento privado...", () -> {
            NativeBridge.stop();
            try (InputStream in=getContentResolver().openInputStream(uri)) {
                if (in==null) throw new IOException("No se pudo leer el archivo seleccionado");
                FirmwareImporter.importZip(in,romDirectory);
            }
        });
    }
    private void updateControls() {
        if (startButton==null) return;
        int state=NativeBridge.state(); boolean busy=pending>0; boolean active=state==1||state==2;
        startButton.setEnabled(!busy&&!active&&FirmwareImporter.ready(romDirectory));
        stopButton.setEnabled(!busy&&(active||state==3)); importButton.setEnabled(!busy);
        buffers.setEnabled(!busy&&!active); outputs.setEnabled(!busy&&!active); separateDsp.setEnabled(!busy&&!active);
        for (View v:playingControls) v.setEnabled(!busy&&state==2);
        firmware.setText(FirmwareImporter.ready(romDirectory)?"ROMs importadas · flash 1 MB + arranque DSP · pantalla encendida mientras usás la app":"Sin ROMs · importar un ZIP con flash.bin y boot-362.bin (o boot-362.ms2000.bin)");
    }
    private void copyDiagnostic() {
        String s="RiMS2000R 0.1.0-alpha\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n"+NativeBridge.stats()+"\n"+midi.description();
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("RiMS2000R diagnóstico",s));
        Toast.makeText(this,"Diagnóstico copiado",Toast.LENGTH_SHORT).show();
    }
    private void about() {
        new AlertDialog.Builder(this).setTitle("RiMS2000R Android Alpha")
            .setMessage("Adaptación Android de RiNoize. Núcleo MS2000R de Tamás Bársony / Tuth, con DSP56300 de The Usual Suspects.\n\nGPL-3.0. Las ROMs no están incluidas. No afiliado ni avalado por Korg.\n\nEsta alpha prueba audio y MIDI. No incluye todavía panel completo, biblioteca SysEx ni entrada de audio/vocoder.")
            .setPositiveButton("Cerrar",null).setNeutralButton("Licencia",(d,w) -> {
                try (InputStream in=getAssets().open("LICENSE.md")) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
                    while ((n=in.read(b))!=-1) out.write(b,0,n);
                    TextView view=text(out.toString(StandardCharsets.UTF_8.name()),12); ScrollView scroll=new ScrollView(this); scroll.addView(view);
                    new AlertDialog.Builder(this).setTitle("GNU GPL v3").setView(scroll).setPositiveButton("Cerrar",null).show();
                } catch (IOException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
            }).show();
    }
    private int noteForKey(int key) {
        int[] keys={KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_D,KeyEvent.KEYCODE_F,KeyEvent.KEYCODE_G,KeyEvent.KEYCODE_H,KeyEvent.KEYCODE_J,KeyEvent.KEYCODE_K};
        int[] notes={60,62,64,65,67,69,71,72}; for(int i=0;i<keys.length;i++) if(keys[i]==key)return notes[i]; return -1;
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        int note=noteForKey(key);
        if (midi!=null&&note>=0&&NativeBridge.state()==2) {
            if(event.getRepeatCount()==0) { int channel=channels.getSelectedItemPosition(); keyboardChannels.put(key,channel); note(channel,note,true); }
            return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyUp(int key,KeyEvent event) {
        Integer channel=keyboardChannels.remove(key);
        if(channel!=null) { note(channel,noteForKey(key),false); return true; }
        return super.onKeyUp(key,event);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; if(midi!=null) { ui.removeCallbacks(refresh); ui.post(refresh); } }
    @Override protected void onPause() { resumed=false; ui.removeCallbacks(refresh); if(midi!=null) NativeBridge.panic(); keyboardChannels.clear(); super.onPause(); }
    @Override protected void onStop() { if(midi!=null&&!destroyed) stopEngine(); super.onStop(); }
    @Override protected void onDestroy() {
        destroyed=true; ui.removeCallbacks(refresh);
        if(midi!=null) { midi.close(); audio.unregisterAudioDeviceCallback(audioCallback); tasks.execute(NativeBridge::stop); }
        tasks.shutdown(); super.onDestroy();
    }
}
