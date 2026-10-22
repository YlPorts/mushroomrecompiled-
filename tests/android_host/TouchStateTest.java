package org.mushroomrecompiled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class TouchStateTest {
    private static void equal(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args)throws Exception {
        TouchState s=new TouchState();
        s.update(9,TouchState.A);s.update(31,TouchState.B);equal(s.mask(),TouchState.A|TouchState.B);
        s.release(9);equal(s.mask(),TouchState.B);s.update(42,TouchState.B);s.release(31);equal(s.mask(),TouchState.B);
        s.cancel();equal(s.mask(),0);
        equal(TouchState.dpad(-70,-70,100),TouchState.LEFT|TouchState.UP);
        equal(TouchState.dpad(70,70,100),TouchState.RIGHT|TouchState.DOWN);
        equal(TouchState.dpad(0,0,100),0);equal(TouchState.dpad(101,0,100),0);
        s.update(4,TouchState.dpad(-60,0,100));equal(s.mask(),TouchState.LEFT);
        s.update(4,TouchState.dpad(60,0,100));equal(s.mask(),TouchState.RIGHT);s.release(4);equal(s.mask(),0);
        for(int i=0;i<10000;i++){s.update(i,TouchState.A);s.update(i+1,TouchState.B);s.release(i);equal(s.mask(),TouchState.B);s.cancel();equal(s.mask(),0);}
        TouchGeometry g=new TouchGeometry();
        g.x[0]=100;g.y[0]=100;g.r[0]=50;
        g.x[1]=300;g.y[1]=200;g.r[1]=50;g.x[2]=360;g.y[2]=200;g.r[2]=50;
        g.x[3]=500;g.y[3]=200;g.r[3]=20;g.x[4]=600;g.y[4]=200;g.r[4]=20;
        g.x[5]=800;g.y[5]=20;g.r[5]=20;
        equal(g.sample(100,40,0),TouchState.UP); // A captured D-pad finger can move beyond its visible rim.
        equal(g.sample(100,20,0),0);equal(g.sample(60,140,0),TouchState.LEFT|TouchState.DOWN);
        equal(g.sample(100,100,0),0);
        equal(g.sample(300,200,1),TouchState.B);equal(g.sample(360,200,2),TouchState.A);
        int overlap=g.sample(330,200,1);if(overlap!=TouchState.A&&overlap!=TouchState.B)throw new AssertionError("One finger activated A+B");
        s.update(7,g.sample(300,200,1));s.update(31,g.sample(360,200,2));equal(s.mask(),TouchState.A|TouchState.B);
        s.release(31);equal(s.mask(),TouchState.B);s.cancel();equal(s.mask(),0);
        equal(g.sample(525,200,3),TouchState.SELECT);equal(g.sample(625,200,4),TouchState.START);
        equal(g.sample(300,400,1),0);equal(g.pick(800,20,true),5);
        for(int n:new int[]{0,16,RomValidator.SIZE-1,RomValidator.SIZE,RomValidator.SIZE+1}){
            try{RomValidator.read(new ByteArrayInputStream(new byte[n]));throw new AssertionError("Accepted invalid ROM size "+n);}catch(IOException expected){}
        }
        if(args.length>0){byte[] rom=Files.readAllBytes(Path.of(args[0]));RomValidator.validate(rom);rom[8]^=1;try{RomValidator.validate(rom);throw new AssertionError("Accepted modified header with identical payload CRC");}catch(IOException expected){}rom[8]^=1;rom[16]^=1;try{RomValidator.validate(rom);throw new AssertionError("Accepted modified payload");}catch(IOException expected){}}
        System.out.println("Touch IDs, multi-touch, sliding diagonals, cancellation and ROM validation passed.");
    }
}
