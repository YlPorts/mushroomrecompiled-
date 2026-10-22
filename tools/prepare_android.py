#!/usr/bin/env python3
"""Fetch pinned SDL2 sources and copy its Android glue (no private game data)."""
import hashlib
from pathlib import Path
import shutil
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = "2.30.12"
SHA256 = "ac356ea55e8b9dd0b2d1fa27da40ef7e238267ccf9324704850d5d47375b48ea"

def main():
    deps = ROOT / "build/dependencies"
    deps.mkdir(parents=True, exist_ok=True)
    archive = deps / f"SDL2-{VERSION}.tar.gz"
    source = deps / f"SDL2-{VERSION}"
    if not archive.is_file():
        urllib.request.urlretrieve(f"https://github.com/libsdl-org/SDL/releases/download/release-{VERSION}/{archive.name}", archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError("SDL2 checksum mismatch. Remove the archive and retry.")
    if not source.is_dir():
        with tarfile.open(archive) as tar:
            tar.extractall(deps, filter="data")
    app = ROOT / "android/app/src/main"
    shutil.copytree(source / "android-project/app/src/main/java/org/libsdl", app / "java/org/libsdl", dirs_exist_ok=True)
    # Android 14+ requires explicit receiver export flags in SDL's Java glue.
    hid = app / "java/org/libsdl/app/HIDDeviceManager.java"
    code = hid.read_text()
    for receiver, flag in (("mUsbBroadcast", "RECEIVER_NOT_EXPORTED"), ("mBluetoothBroadcast", "RECEIVER_EXPORTED")):
        old = f"        mContext.registerReceiver({receiver}, filter);"
        if code.count(old) != 1:
            raise RuntimeError("SDL Java receiver patch no longer matches the pinned source")
        exported = "true" if flag == "RECEIVER_EXPORTED" else "false"
        new = f"        org.mushroomrecompiled.AndroidReceivers.register(mContext, {receiver}, filter, {exported});"
        code = code.replace(old, new)
    replacements = {
        "mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT)": "org.mushroomrecompiled.BluetoothAccess.connected(mBluetoothManager, BluetoothProfile.GATT)",
        "btAdapter.getBondedDevices()": "org.mushroomrecompiled.BluetoothAccess.bonded(btAdapter)",
        'bluetoothDevice.getName().equals("SteamController")': '"SteamController".equals(org.mushroomrecompiled.BluetoothAccess.name(bluetoothDevice))',
        "bluetoothDevice.getName()": "org.mushroomrecompiled.BluetoothAccess.name(bluetoothDevice)",
        "bluetoothDevice.getType()": "org.mushroomrecompiled.BluetoothAccess.type(bluetoothDevice)",
    }
    for old, new in replacements.items():
        if old not in code: raise RuntimeError("SDL manager permission patch no longer matches")
        code = code.replace(old, new)
    hid.write_text(code)
    ble = app / "java/org/libsdl/app/HIDDeviceBLESteamController.java"
    code = ble.read_text()
    replacements = {
        "mGatt.readCharacteristic(chr)": "org.mushroomrecompiled.BluetoothAccess.read(mGatt, chr)",
        "mGatt.writeCharacteristic(chr)": "org.mushroomrecompiled.BluetoothAccess.write(mGatt, chr)",
        "mGatt.setCharacteristicNotification(chr, true)": "org.mushroomrecompiled.BluetoothAccess.notify(mGatt, chr, true)",
        "mGatt.writeDescriptor(cccd)": "org.mushroomrecompiled.BluetoothAccess.descriptor(mGatt, cccd)",
        "mDevice.connectGatt(mManager.getContext(), managed, this, TRANSPORT_LE)": "org.mushroomrecompiled.BluetoothAccess.connect(mDevice, mManager.getContext(), managed, this, TRANSPORT_LE)",
        "mDevice.connectGatt(mManager.getContext(), managed, this)": "org.mushroomrecompiled.BluetoothAccess.connectLegacy(mDevice, mManager.getContext(), managed, this)",
        "btManager.getConnectionState(mDevice, BluetoothProfile.GATT)": "org.mushroomrecompiled.BluetoothAccess.connectionState(btManager, mDevice, BluetoothProfile.GATT)",
        "mGatt.disconnect()": "org.mushroomrecompiled.BluetoothAccess.disconnect(mGatt)",
        "gatt.disconnect()": "org.mushroomrecompiled.BluetoothAccess.disconnect(gatt)",
        "g.disconnect()": "org.mushroomrecompiled.BluetoothAccess.disconnect(g)",
        "g.close()": "org.mushroomrecompiled.BluetoothAccess.close(g)",
        "mGatt.discoverServices()": "org.mushroomrecompiled.BluetoothAccess.discover(mGatt)",
        "gatt.writeCharacteristic(reportChr)": "org.mushroomrecompiled.BluetoothAccess.write(gatt, reportChr)",
        "mGatt.getServices()": "org.mushroomrecompiled.BluetoothAccess.services(mGatt)",
        "gatt.getServices()": "org.mushroomrecompiled.BluetoothAccess.services(gatt)",
        "mGatt.getService(steamControllerService)": "org.mushroomrecompiled.BluetoothAccess.service(mGatt, steamControllerService)",
    }
    for old, new in replacements.items():
        if old not in code: raise RuntimeError("SDL BLE permission patch no longer matches")
        code = code.replace(old, new)
    code = code.replace("chr = getCharacteristic(mUuid);", "chr = getCharacteristic(mUuid);\n                    if (chr == null) { mResult = false; return; }")
    ble.write_text(code)
    audio = app / "java/org/libsdl/app/SDLAudioManager.java"
    code = audio.read_text()
    old = """                mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate,
                        channelConfig, audioFormat, desiredFrames * frameSize);"""
    new = """                // Mushroom only opens playback. Reject an accidental capture request
                // without requesting a microphone permission the game does not use.
                if (mContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
                try {
                    mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate,
                            channelConfig, audioFormat, desiredFrames * frameSize);
                } catch (SecurityException revoked) { return null; }"""
    if old not in code: raise RuntimeError("SDL capture permission patch no longer matches")
    code = code.replace(old, new)
    code = code.replace("    protected static int[] open(boolean isCapture,", "    @android.annotation.SuppressLint(\"MissingPermission\") // Capture is permission-checked and unused by this game.\n    protected static int[] open(boolean isCapture,")
    audio.write_text(code)
    activity = app / "java/org/libsdl/app/SDLActivity.java"
    code = activity.read_text().replace("    public void onBackPressed() {", "    @android.annotation.SuppressLint(\"GestureBackNavigation\") // GameActivity registers OnBackInvokedCallback for API 33+.\n    public void onBackPressed() {")
    activity.write_text(code)
    assets = app / "assets"
    assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "assets/gamecontrollerdb.txt", assets / "gamecontrollerdb.txt")
    shutil.copy2(ROOT / "png.png", assets / "underplay.png")
    licenses = assets / "licenses"
    licenses.mkdir(exist_ok=True)
    shutil.copy2(ROOT / "LICENSE", licenses / "MushroomRecompiled-MIT.txt")
    shutil.copy2(ROOT / "tools/nesrecomp-src/LICENSE", licenses / "NESRecomp-PolyForm-Noncommercial.txt")
    shutil.copy2(source / "LICENSE.txt", licenses / "SDL2-zlib.txt")
    for license_file in (ROOT / "LICENSES").glob("*"):
        if license_file.is_file():
            shutil.copy2(license_file, licenses / license_file.name)
    print(f"SDL {VERSION} verified; Android glue and licenses prepared.")

if __name__ == "__main__":
    main()
