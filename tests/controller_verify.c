#include <SDL.h>
#include "input.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

int main(void){
    assert(input_init());
    int index=SDL_JoystickAttachVirtual(SDL_JOYSTICK_TYPE_GAMECONTROLLER,2,16,1);assert(index>=0);
    SDL_JoystickGUID guid=SDL_JoystickGetDeviceGUID(index);char text[33],mapping[1024];SDL_JoystickGetGUIDString(guid,text,sizeof text);
    snprintf(mapping,sizeof mapping,"%s,Port test pad,a:b0,b:b1,x:b2,y:b3,back:b6,start:b7,guide:b8,leftx:a0,lefty:a1,dpup:h0.1,dpdown:h0.4,dpleft:h0.8,dpright:h0.2,",text);
    assert(SDL_GameControllerAddMapping(mapping)>=0);
    SDL_Joystick *virtual=SDL_JoystickOpen(index);assert(virtual);
    input_refresh();assert(input_read().connected);
    assert(SDL_JoystickSetVirtualButton(virtual,0,1)==0);assert(SDL_JoystickSetVirtualButton(virtual,2,1)==0);
    input_refresh();assert((input_read().pad&(INPUT_A|INPUT_B))==(INPUT_A|INPUT_B));
    SDL_JoystickSetVirtualButton(virtual,0,0);input_refresh();assert(!(input_read().pad&INPUT_A));assert(input_read().pad&INPUT_B);
    SDL_JoystickSetVirtualButton(virtual,2,0);SDL_JoystickSetVirtualHat(virtual,0,SDL_HAT_RIGHTUP);input_refresh();
    assert((input_read().pad&(INPUT_RIGHT|INPUT_UP))==(INPUT_RIGHT|INPUT_UP));
    SDL_JoystickSetVirtualHat(virtual,0,0);SDL_JoystickSetVirtualAxis(virtual,0,-32767);input_refresh();assert(input_read().pad&INPUT_LEFT);
    SDL_JoystickSetVirtualAxis(virtual,0,0);input_refresh();assert(!input_read().pad);
    int bindings[11];for(int i=0;i<11;i++)bindings[i]=-1;bindings[0]=4;input_configure(bindings);
    SDL_JoystickSetVirtualButton(virtual,2,1);input_refresh();assert(!(input_read().pad&INPUT_B));
    SDL_JoystickSetVirtualButton(virtual,4,1);input_refresh();assert(input_read().pad&INPUT_B);assert(input_first_pressed()>=0);
    SDL_Event pushed={0};pushed.type=SDL_USEREVENT;pushed.user.code=1234;assert(SDL_PushEvent(&pushed)==1);input_refresh();
    SDL_Event received;int found=0;while(SDL_PollEvent(&received))if(received.type==SDL_USEREVENT&&received.user.code==1234)found=1;
    assert(found); // Refresh must not steal lifecycle, menu, or renderer events.
    SDL_JoystickClose(virtual);assert(SDL_JoystickDetachVirtual(index)==0);input_refresh();assert(!input_read().connected&&!input_read().pad);
    input_close();puts("SDL virtual GameController: A+B, release, diagonals, analog, remap, event preservation and hotplug passed.");
}
