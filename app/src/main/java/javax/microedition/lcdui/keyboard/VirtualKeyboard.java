/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2021 Nikita Shakarun
 * Copyright 2019-2023 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.microedition.lcdui.keyboard;

import static javax.microedition.lcdui.keyboard.KeyMapper.SE_KEY_SPECIAL_GAMING_A;
import static javax.microedition.lcdui.keyboard.KeyMapper.SE_KEY_SPECIAL_GAMING_B;

import android.content.SharedPreferences;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.lcdui.overlay.Overlay;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;

public class VirtualKeyboard implements Overlay, Runnable {
	private static final String TAG = VirtualKeyboard.class.getSimpleName();

	private static final String ARROW_LEFT = "←";
	private static final String ARROW_UP = "↑";
	private static final String ARROW_RIGHT = "→";
	private static final String ARROW_DOWN = "↓";
	private static final String ARROW_UP_LEFT = "↖";
	private static final String ARROW_UP_RIGHT = "↗";
	private static final String ARROW_DOWN_LEFT = "↙";
	private static final String ARROW_DOWN_RIGHT = "↘";

	private static final int LAYOUT_SIGNATURE = 0x564B4C00;
	private static final int LAYOUT_VERSION = 3;
	public static final int LAYOUT_EOF = -1;
	public static final int LAYOUT_KEYS = 0;
	public static final int LAYOUT_SCALES = 1;
	@SuppressWarnings("unused")
	public static final int LAYOUT_COLORS = 2;
	public static final int LAYOUT_TYPE = 3;

	private static final int SHAPE_OVAL = 0;
	private static final int SHAPE_RECT = 1;
	public static final int SHAPE_ROUND_RECT = 2;

	public static final int TYPE_CUSTOM = 0;
	private static final int TYPE_PHONE = 1;
	private static final int TYPE_PHONE_ARROWS = 2;
	private static final int TYPE_NUM_ARR = 3;
	private static final int TYPE_ARR_NUM = 4;
	private static final int TYPE_NUMBERS = 5;
	private static final int TYPE_ARROWS = 6;
	private static final int TYPE_JOYSTICK = 7;

	private static final float PHONE_KEY_ROWS = 5;
	private static final float PHONE_KEY_SCALE_X = 2.0f;
	private static final float PHONE_KEY_SCALE_Y = 0.75f;
	private static final long[] REPEAT_INTERVALS = {200, 400, 128, 128, 128, 128, 128};

	private static final int SCREEN = -1;
	private static final int KEY_NUM1 = 0;
	private static final int KEY_NUM2 = 1;
	private static final int KEY_NUM3 = 2;
	private static final int KEY_NUM4 = 3;
	private static final int KEY_NUM5 = 4;
	private static final int KEY_NUM6 = 5;
	private static final int KEY_NUM7 = 6;
	private static final int KEY_NUM8 = 7;
	private static final int KEY_NUM9 = 8;
	private static final int KEY_NUM0 = 9;
	private static final int KEY_STAR = 10;
	private static final int KEY_POUND = 11;
	private static final int KEY_SOFT_LEFT = 12;
	private static final int KEY_SOFT_RIGHT = 13;
	private static final int KEY_D = 14;
	private static final int KEY_C = 15;
	private static final int KEY_UP_LEFT = 16;
	private static final int KEY_UP = 17;
	private static final int KEY_UP_RIGHT = 18;
	private static final int KEY_LEFT = 19;
	private static final int KEY_RIGHT = 20;
	private static final int KEY_DOWN_LEFT = 21;
	private static final int KEY_DOWN = 22;
	private static final int KEY_DOWN_RIGHT = 23;
	private static final int KEY_FIRE = 24;
	private static final int KEY_A = 25;
	private static final int KEY_B = 26;
	private static final int KEY_MENU = 27;
	private static final int KEYBOARD_SIZE = 28;

	private static final float SCALE_SNAP_RADIUS = 0.05f;

	private static final int FEEDBACK_DURATION = 50;

	private final float[] keyScales = {
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
	};
	private final int[][] keyScaleGroups = {{
			KEY_UP_LEFT,
			KEY_UP,
			KEY_UP_RIGHT,
			KEY_LEFT,
			KEY_RIGHT,
			KEY_DOWN_LEFT,
			KEY_DOWN,
			KEY_DOWN_RIGHT
	}, {
			KEY_SOFT_LEFT,
			KEY_SOFT_RIGHT
	}, {
			KEY_A,
			KEY_B,
			KEY_C,
			KEY_D,
	}, {
			KEY_NUM1,
			KEY_NUM2,
			KEY_NUM3,
			KEY_NUM4,
			KEY_NUM5,
			KEY_NUM6,
			KEY_NUM7,
			KEY_NUM8,
			KEY_NUM9,
			KEY_NUM0,
			KEY_STAR,
			KEY_POUND
	}, {
			KEY_FIRE
	}, {
			KEY_MENU
	}};
	private final VirtualKey[] keypad = new VirtualKey[KEYBOARD_SIZE];
	// the average user usually has no more than 10 fingers...
	private final VirtualKey[] associatedKeys = new VirtualKey[10];
	private final int[] snapStack = new int[KEYBOARD_SIZE];

	private final Handler handler;
	private final File saveFile;
	private final ProfileModel settings;
	private final RectF virtualScreen = new RectF();

	private Canvas target;
	private View overlayView;
	private boolean obscuresVirtualScreen;
	private boolean visible = true;
	private int layoutEditMode = LAYOUT_EOF;
	private int editedIndex;
	private float offsetX;
	private float offsetY;
	private float prevScaleX;
	private float prevScaleY;
	private RectF screen;
	private float keySize =
			Math.min(ContextHolder.getDisplayWidth(), ContextHolder.getDisplayHeight()) / 6.0f;
	private float snapRadius;
	private int layoutVariant;

	// Joystick fields
	private boolean joystickVisible = true;
	private int joystickKeyUp = Canvas.KEY_UP;
	private int joystickKeyDown = Canvas.KEY_DOWN;
	private int joystickKeyLeft = Canvas.KEY_LEFT;
	private int joystickKeyRight = Canvas.KEY_RIGHT;
	private final RectF joystickRect = new RectF();
	private float joystickCenterX;
	private float joystickCenterY;
	private float joystickRadius;
	private float joystickThumbRadius;
	private float joystickThumbX;
	private float joystickThumbY;
	private boolean joystickActive = false;
	private int joystickPointer = -1;
	private boolean joystickDragMode = false;
	private float joystickDragOffsetX;
	private float joystickDragOffsetY;
	private boolean joystickResizeMode = false;
	private float joystickResizeStartDist;
	private float joystickResizeStartRadius;
	// Track which joystick directions are currently pressed
	private boolean jsUp, jsDown, jsLeft, jsRight;

	private static final String PREF_JOYSTICK_VISIBLE = "joystick_visible";
	private static final String PREF_JOYSTICK_X = "joystick_x";
	private static final String PREF_JOYSTICK_Y = "joystick_y";
	private static final String PREF_JOYSTICK_RADIUS = "joystick_radius";
	private static final String PREF_JOYSTICK_KEY_UP = "joystick_key_up";
	private static final String PREF_JOYSTICK_KEY_DOWN = "joystick_key_down";
	private static final String PREF_JOYSTICK_KEY_LEFT = "joystick_key_left";
	private static final String PREF_JOYSTICK_KEY_RIGHT = "joystick_key_right";

	public VirtualKeyboard(ProfileModel settings) {
		this.settings = settings;
		this.saveFile = new File(settings.dir + Config.MIDLET_KEY_LAYOUT_FILE);

		for (int i = KEY_NUM1; i < 9; i++) {
			keypad[i] = new VirtualKey(Canvas.KEY_NUM1 + i, Integer.toString(1 + i));
		}

		keypad[KEY_NUM0] = new VirtualKey(Canvas.KEY_NUM0, "0");
		keypad[KEY_STAR] = new VirtualKey(Canvas.KEY_STAR, "*");
		keypad[KEY_POUND] = new VirtualKey(Canvas.KEY_POUND, "#");

		keypad[KEY_SOFT_LEFT] = new VirtualKey(Canvas.KEY_SOFT_LEFT, "L");
		keypad[KEY_SOFT_RIGHT] = new VirtualKey(Canvas.KEY_SOFT_RIGHT, "R");

		keypad[KEY_A] = new VirtualKey(SE_KEY_SPECIAL_GAMING_A, "A");
		keypad[KEY_B] = new VirtualKey(SE_KEY_SPECIAL_GAMING_B, "B");
		keypad[KEY_C] = new VirtualKey(Canvas.KEY_END, "C");
		keypad[KEY_D] = new VirtualKey(Canvas.KEY_SEND, "D");

		keypad[KEY_UP_LEFT] = new DualKey(Canvas.KEY_UP, Canvas.KEY_LEFT, ARROW_UP_LEFT);
		keypad[KEY_UP] = new VirtualKey(Canvas.KEY_UP, ARROW_UP);
		keypad[KEY_UP_RIGHT] = new DualKey(Canvas.KEY_UP, Canvas.KEY_RIGHT, ARROW_UP_RIGHT);

		keypad[KEY_LEFT] = new VirtualKey(Canvas.KEY_LEFT, ARROW_LEFT);
		keypad[KEY_RIGHT] = new VirtualKey(Canvas.KEY_RIGHT, ARROW_RIGHT);

		keypad[KEY_DOWN_LEFT] = new DualKey(Canvas.KEY_DOWN, Canvas.KEY_LEFT, ARROW_DOWN_LEFT);
		keypad[KEY_DOWN] = new VirtualKey(Canvas.KEY_DOWN, ARROW_DOWN);
		keypad[KEY_DOWN_RIGHT] = new DualKey(Canvas.KEY_DOWN, Canvas.KEY_RIGHT, ARROW_DOWN_RIGHT);

		keypad[KEY_FIRE] = new VirtualKey(Canvas.KEY_FIRE, "F");
		keypad[KEY_MENU] = new MenuKey();

		layoutVariant = readLayoutType();

		if (layoutVariant == -1) {
			layoutVariant = settings.vkType;
			if (layoutVariant == TYPE_CUSTOM) {
				layoutVariant = TYPE_NUM_ARR;
			}
		}

		// Load joystick settings BEFORE resetLayout so joystickRadius is available
		loadJoystickSettings();

		resetLayout(layoutVariant);
		if (layoutVariant == TYPE_CUSTOM) {
			try {
				readLayout();
			} catch (IOException e) {
				e.printStackTrace();
				resetLayout(TYPE_NUM_ARR);
				layoutVariant = TYPE_NUM_ARR;
				saveLayout();
			}
		}
		HandlerThread thread = new HandlerThread("MidletVirtualKeyboard");
		thread.start();
		handler = new Handler(thread.getLooper());

		// Initialize joystick position after settings are loaded
		if (isJoystick()) {
			initJoystickPosition();
		}
	}

	private void initJoystickPosition() {
		float sw = screen != null ? screen.width() : ContextHolder.getDisplayWidth();
		float sh = screen != null ? screen.height() : ContextHolder.getDisplayHeight();
		if (joystickCenterX < 0 || joystickCenterY < 0
				|| joystickCenterX - joystickRadius < -joystickRadius * 0.5f
				|| joystickCenterY - joystickRadius < -joystickRadius * 0.5f
				|| joystickCenterX + joystickRadius > sw + joystickRadius * 0.5f
				|| joystickCenterY + joystickRadius > sh + joystickRadius * 0.5f) {
			joystickCenterX = joystickRadius + 20;
			joystickCenterY = sh - joystickRadius - 20;
		}
		joystickThumbX = joystickCenterX;
		joystickThumbY = joystickCenterY;
		joystickThumbRadius = joystickRadius * 0.35f;
		updateJoystickRect();
	}

	private void loadJoystickSettings() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
				ContextHolder.getAppContext());
		String prefix = settings.dir.getAbsolutePath();
		joystickVisible = prefs.getBoolean(prefix + PREF_JOYSTICK_VISIBLE, true);
		joystickKeyUp = prefs.getInt(prefix + PREF_JOYSTICK_KEY_UP, Canvas.KEY_UP);
		joystickKeyDown = prefs.getInt(prefix + PREF_JOYSTICK_KEY_DOWN, Canvas.KEY_DOWN);
		joystickKeyLeft = prefs.getInt(prefix + PREF_JOYSTICK_KEY_LEFT, Canvas.KEY_LEFT);
		joystickKeyRight = prefs.getInt(prefix + PREF_JOYSTICK_KEY_RIGHT, Canvas.KEY_RIGHT);
		float defaultRadius = Math.min(ContextHolder.getDisplayWidth(), ContextHolder.getDisplayHeight()) / 5.0f;
		joystickRadius = prefs.getFloat(prefix + PREF_JOYSTICK_RADIUS, defaultRadius);
		joystickCenterX = prefs.getFloat(prefix + PREF_JOYSTICK_X, -1);
		joystickCenterY = prefs.getFloat(prefix + PREF_JOYSTICK_Y, -1);
		joystickThumbRadius = joystickRadius * 0.35f;
	}

	private void saveJoystickSettings() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
				ContextHolder.getAppContext());
		String prefix = settings.dir.getAbsolutePath();
		prefs.edit()
				.putBoolean(prefix + PREF_JOYSTICK_VISIBLE, joystickVisible)
				.putInt(prefix + PREF_JOYSTICK_KEY_UP, joystickKeyUp)
				.putInt(prefix + PREF_JOYSTICK_KEY_DOWN, joystickKeyDown)
				.putInt(prefix + PREF_JOYSTICK_KEY_LEFT, joystickKeyLeft)
				.putInt(prefix + PREF_JOYSTICK_KEY_RIGHT, joystickKeyRight)
				.putFloat(prefix + PREF_JOYSTICK_RADIUS, joystickRadius)
				.putFloat(prefix + PREF_JOYSTICK_X, joystickCenterX)
				.putFloat(prefix + PREF_JOYSTICK_Y, joystickCenterY)
				.apply();
	}

	public boolean isJoystick() {
		return layoutVariant == TYPE_JOYSTICK;
	}

	public int getJoystickKeyUp() {
		return joystickKeyUp;
	}

	public int getJoystickKeyDown() {
		return joystickKeyDown;
	}

	public int getJoystickKeyLeft() {
		return joystickKeyLeft;
	}

	public int getJoystickKeyRight() {
		return joystickKeyRight;
	}

	public void setJoystickKeyMappings(int up, int down, int left, int right) {
		joystickKeyUp = up;
		joystickKeyDown = down;
		joystickKeyLeft = left;
		joystickKeyRight = right;
		saveJoystickSettings();
	}

	private void updateJoystickRect() {
		joystickRect.set(
				joystickCenterX - joystickRadius,
				joystickCenterY - joystickRadius,
				joystickCenterX + joystickRadius,
				joystickCenterY + joystickRadius
		);
	}

	public void onLayoutChanged(int variant) {
		if (variant == TYPE_CUSTOM && isPhone()) {
			float min = screen.width();
			float max = screen.height();
			if (min > max) {
				float tmp = max;
				max = min;
				min = tmp;
			}

			float oldSize = min / 6.0f;
			float newSize = Math.min(oldSize, max / 12.0f);
			float s = oldSize / newSize;
			for (int i = 0; i < keyScales.length; i++) {
				keyScales[i] *= s;
			}
		}
		layoutVariant = variant;
		saveLayout();
		if (target != null && target.isShown()) {
			target.updateSize();
		}
	}

	private void resetLayout(int variant) {
		switch (variant) {
			case TYPE_PHONE -> {
				for (int j = 0, len = keyScales.length; j < len; ) {
					keyScales[j++] = PHONE_KEY_SCALE_X;
					keyScales[j++] = PHONE_KEY_SCALE_Y;
				}

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_FIRE, KEY_NUM2, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_C, KEY_A, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_D, KEY_B, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP_LEFT, KEY_C, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP, KEY_C, RectSnap.EXT_SOUTHEAST, false);
				setSnap(KEY_UP_RIGHT, KEY_D, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_LEFT, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN, KEY_MENU, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_RIGHT, RectSnap.EXT_SOUTH, false);
			}
			case TYPE_PHONE_ARROWS -> {
				for (int j = 0, len = keyScales.length; j < len; ) {
					keyScales[j++] = PHONE_KEY_SCALE_X;
					keyScales[j++] = PHONE_KEY_SCALE_Y;
				}

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_DOWN, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_FIRE, KEY_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_RIGHT, KEY_FIRE, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_UP, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_C, KEY_A, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_D, KEY_B, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP_LEFT, KEY_C, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM2, KEY_C, RectSnap.EXT_SOUTHEAST, false);
				setSnap(KEY_UP_RIGHT, KEY_D, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM4, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
			}
			// case TYPE_NUM_ARR,
			default -> {
				Arrays.fill(keyScales, 1.0f);

				setSnap(KEY_DOWN_RIGHT, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_DOWN, KEY_DOWN_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_DOWN_LEFT, KEY_DOWN, RectSnap.EXT_WEST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_STAR, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_NUM0, KEY_STAR, RectSnap.EXT_EAST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM3, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, false);
			}
			case TYPE_ARR_NUM -> {
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN_LEFT, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_POUND, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_NUM0, KEY_POUND, RectSnap.EXT_WEST, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM3, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, false);
			}
			case TYPE_NUMBERS -> {
				Arrays.fill(keyScales, 1);

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_WEST, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_EAST, true);

				setSnap(KEY_UP, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, false);
				setSnap(KEY_UP_RIGHT, KEY_UP, RectSnap.EXT_EAST, false);
				setSnap(KEY_FIRE, KEY_UP, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_LEFT, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, false);
				setSnap(KEY_A, KEY_NUM4, RectSnap.EXT_WEST, false);
				setSnap(KEY_B, KEY_NUM6, RectSnap.EXT_EAST, false);
				setSnap(KEY_C, KEY_NUM7, RectSnap.EXT_WEST, false);
				setSnap(KEY_D, KEY_NUM9, RectSnap.EXT_EAST, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTHEAST, false);
			}
			case TYPE_ARROWS -> {
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_LEFT, KEY_DOWN, RectSnap.EXT_WEST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_WEST, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_EAST, true);

				setSnap(KEY_NUM1, KEY_NUM2, RectSnap.EXT_WEST, false);
				setSnap(KEY_NUM2, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, false);
				setSnap(KEY_NUM4, KEY_NUM1, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_NUM3, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM7, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM9, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_STAR, KEY_NUM7, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM0, KEY_NUM8, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_POUND, KEY_NUM9, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_A, KEY_LEFT, RectSnap.EXT_WEST, false);
				setSnap(KEY_B, KEY_RIGHT, RectSnap.EXT_EAST, false);
				setSnap(KEY_C, KEY_DOWN_LEFT, RectSnap.EXT_WEST, false);
				setSnap(KEY_D, KEY_DOWN_RIGHT, RectSnap.EXT_EAST, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTHEAST, false);
			}
			case TYPE_JOYSTICK -> {
				Arrays.fill(keyScales, 1);

				// Joystick replaces D-pad (drawn separately via paintJoystick)
				// Soft keys at bottom corners
				setSnap(KEY_SOFT_LEFT, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_SOFT_RIGHT, SCREEN, RectSnap.INT_SOUTHEAST, true);

				// Bottom row: *, 0, # built leftward from soft_right
				setSnap(KEY_POUND, KEY_SOFT_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM0, KEY_POUND, RectSnap.EXT_WEST, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);

				// Fire above center of bottom row
				setSnap(KEY_FIRE, KEY_NUM0, RectSnap.EXT_NORTH, true);

				// Keys 1,3 diagonal above Fire (quarter-arc shape)
				setSnap(KEY_NUM1, KEY_FIRE, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_NUM3, KEY_FIRE, RectSnap.EXT_NORTHEAST, true);

				// Keys 7,9 above 1,3
				setSnap(KEY_NUM7, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM9, KEY_NUM3, RectSnap.EXT_NORTH, true);

				// Hide all other keys (snap to SCREEN so they don't interfere)
				setSnap(KEY_NUM2, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM4, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM5, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM6, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM8, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_UP, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_DOWN, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_LEFT, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_RIGHT, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_UP_LEFT, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_UP_RIGHT, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_DOWN_LEFT, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_DOWN_RIGHT, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_C, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_D, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTH, false);

				// Initialize joystick position
				initJoystickPosition();
			}
		}
	}

	public int getLayout() {
		return layoutVariant;
	}

	public float getPhoneKeyboardHeight(float w, float h) {
		return PHONE_KEY_ROWS * getKeySize(w, h) * PHONE_KEY_SCALE_Y;
	}

	public void setLayout(int variant) {
		resetLayout(variant);
		if (variant == TYPE_CUSTOM) {
			try {
				readLayout();
			} catch (IOException ioe) {
				ioe.printStackTrace();
				resetLayout(layoutVariant);
				return;
			}
		}
		layoutVariant = variant;
		onLayoutChanged(variant);
		for (int group = 0; group < keyScaleGroups.length; group++) {
			resizeKeyGroup(group);
		}
		snapKeys();
		overlayView.postInvalidate();
		if (target != null && target.isShown()) {
			target.updateSize();
		}
	}

	private void saveLayout() {
		try (RandomAccessFile raf = new RandomAccessFile(saveFile, "rw")) {
			int variant = layoutVariant;
			if (variant != TYPE_CUSTOM && raf.length() > 16) {
				try {
					if (raf.readInt() != LAYOUT_SIGNATURE) {
						throw new IOException("file signature not found");
					}
					int version = raf.readInt();
					if (version < 1 || version > LAYOUT_VERSION) {
						throw new IOException("incompatible file version");
					}
					loop:while (true) {
						int block = raf.readInt();
						int length = raf.readInt();
						switch (block) {
							case LAYOUT_EOF:
								raf.seek(raf.getFilePointer() - 8);
								raf.writeInt(LAYOUT_TYPE);
								raf.writeInt(1);
								raf.write(variant);
								raf.writeInt(LAYOUT_EOF);
								raf.writeInt(0);
								return;
							case LAYOUT_TYPE:
								raf.write(variant);
								return;
							case LAYOUT_KEYS:
								if (version >= 2) {
									int count = raf.readInt();
									length = count * 21;
								}
							default:
								if (raf.skipBytes(length) != length) {
									break loop;
								}
								break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			raf.seek(0);
			raf.writeInt(LAYOUT_SIGNATURE);
			raf.writeInt(LAYOUT_VERSION);
			raf.writeInt(LAYOUT_TYPE);
			raf.writeInt(1);
			raf.write(variant);
			if (variant != TYPE_CUSTOM) {
				raf.writeInt(LAYOUT_EOF);
				raf.writeInt(0);
				raf.setLength(raf.getFilePointer());
				return;
			}
			raf.writeInt(LAYOUT_KEYS);
			raf.writeInt(keypad.length * 21 + 4);
			raf.writeInt(keypad.length);
			for (VirtualKey key : keypad) {
				raf.writeInt(key.hashCode());
				raf.writeBoolean(key.visible);
				raf.writeInt(key.snapOrigin);
				raf.writeInt(key.snapMode);
				PointF snapOffset = key.snapOffset;
				raf.writeFloat(snapOffset.x);
				raf.writeFloat(snapOffset.y);
			}
			raf.writeInt(LAYOUT_SCALES);
			raf.writeInt(keyScales.length * 4 + 4);
			raf.writeInt(keyScales.length);
			for (float keyScale : keyScales) {
				raf.writeFloat(keyScale);
			}
			raf.writeInt(LAYOUT_EOF);
			raf.writeInt(0);
			raf.setLength(raf.getFilePointer());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int readLayoutType() {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(saveFile))) {
			if (dis.readInt() != LAYOUT_SIGNATURE) {
				throw new IOException("file signature not found");
			}
			int version = dis.readInt();
			if (version < 1 || version > LAYOUT_VERSION) {
				throw new IOException("incompatible file version");
			}
			int custom = 0;
			while (true) {
				int block = dis.readInt();
				int length = dis.readInt();
				switch (block) {
					case LAYOUT_EOF -> {
						return custom == 3 ? 0 : -1;
					}
					case LAYOUT_TYPE -> {
						return dis.read();
					}
					case LAYOUT_KEYS -> {
						if (version >= 2) {
							int count = dis.readInt();
							length = count * 21;
						}
						if (dis.skipBytes(length) != length) {
							return -1;
						}
						custom |= 1;
					}
					case LAYOUT_SCALES -> {
						if (dis.skipBytes(length) != length) {
							return -1;
						}
						custom |= 2;
					}
					default -> {
						if (dis.skipBytes(length) != length) {
							return -1;
						}
					}
				}
			}
		} catch (FileNotFoundException e) {
			Log.w(TAG, "readLayoutType() threw an FileNotFoundException: " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

	private void readLayout() throws IOException {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(saveFile))) {
			if (dis.readInt() != LAYOUT_SIGNATURE) {
				throw new IOException("file signature not found");
			}
			int version = dis.readInt();
			if (version < 1 || version > LAYOUT_VERSION) {
				throw new IOException("incompatible file version");
			}
			while (true) {
				int block = dis.readInt();
				int length = dis.readInt();
				int count;
				switch (block) {
					case LAYOUT_EOF -> {
						return;
					}
					case LAYOUT_KEYS -> {
						count = dis.readInt();
						for (int i = 0; i < count; i++) {
							int hash = dis.readInt();
							boolean found = false;
							for (VirtualKey key : keypad) {
								if (key.hashCode() == hash) {
									if (version >= 2) {
										key.visible = dis.readBoolean();
									}
									key.snapOrigin = dis.readInt();
									key.snapMode = dis.readInt();
									key.snapOffset.x = dis.readFloat();
									key.snapOffset.y = dis.readFloat();
									found = true;
									break;
								}
							}
							if (!found) {
								dis.skipBytes(version >= 2 ? 17 : 16);
							}
						}
					}
					case LAYOUT_SCALES -> {
						count = dis.readInt();
						if (version >= 3) {
							for (int i = 0; i < count; i++) {
								keyScales[i] = dis.readFloat();
							}
						} else if (count * 2 <= keyScales.length) {
							for (int i = 0, len = count * 2; i < len; ) {
								float v = dis.readFloat();
								keyScales[i++] = v;
								keyScales[i++] = v;
							}
						} else {
							dis.skipBytes(count * 4);
						}
					}
					default -> dis.skipBytes(length);
				}
			}
		}
	}

	public String[] getKeyNames() {
		int extra = isJoystick() ? 1 : 0;
		String[] names = new String[KEYBOARD_SIZE + extra];
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			names[i] = keypad[i].label;
		}
		if (extra > 0) {
			names[KEYBOARD_SIZE] = "Joystick";
		}
		return names;
	}

	public boolean[] getKeysVisibility() {
		int extra = isJoystick() ? 1 : 0;
		boolean[] states = new boolean[KEYBOARD_SIZE + extra];
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			states[i] = !keypad[i].visible;
		}
		if (extra > 0) {
			states[KEYBOARD_SIZE] = !joystickVisible;
		}
		return states;
	}

	public void setKeysVisibility(boolean[] states) {
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			keypad[i].visible = !states[i];
		}
		if (isJoystick() && states.length > KEYBOARD_SIZE) {
			joystickVisible = !states[KEYBOARD_SIZE];
			saveJoystickSettings();
		}
		overlayView.postInvalidate();
	}

	public void resetKeyPositions(boolean[] selected) {
		if (screen == null) return;
		float centerX = screen.centerX();
		float centerY = screen.centerY();
		for (int i = 0; i < KEYBOARD_SIZE && i < selected.length; i++) {
			if (selected[i]) {
				VirtualKey key = keypad[i];
				key.snapOrigin = SCREEN;
				key.snapMode = RectSnap.NO_SNAP;
				float kw = key.rect.width();
				float kh = key.rect.height();
				key.rect.set(centerX - kw / 2, centerY - kh / 2,
						centerX + kw / 2, centerY + kh / 2);
				key.snapOffset.set(centerX - screen.centerX(), centerY - screen.centerY());
				key.visible = true;
			}
		}
		// Reset joystick position if selected
		if (isJoystick() && selected.length > KEYBOARD_SIZE && selected[KEYBOARD_SIZE]) {
			joystickCenterX = centerX;
			joystickCenterY = centerY;
			joystickThumbX = joystickCenterX;
			joystickThumbY = joystickCenterY;
			updateJoystickRect();
			joystickVisible = true;
			saveJoystickSettings();
		}
		snapKeys();
		overlayView.postInvalidate();
	}

	@Override
	public void setTarget(Canvas canvas) {
		target = canvas;
		highlightGroup(-1);
	}

	private void setSnap(int key, int origin, int mode, boolean visible) {
		VirtualKey vKey = keypad[key];
		vKey.snapOrigin = origin;
		vKey.snapMode = mode;
		vKey.snapOffset.set(0, 0);
		vKey.snapValid = false;
		vKey.visible = visible;
	}

	private boolean findSnap(int target, int origin) {
		VirtualKey tk = keypad[target];
		VirtualKey ok = keypad[origin];
		tk.snapMode = RectSnap.getSnap(tk.rect, ok.rect, snapRadius, RectSnap.COARSE_MASK, true);
		if (tk.snapMode != RectSnap.NO_SNAP) {
			tk.snapOrigin = origin;
			tk.snapOffset.set(0, 0);
			for (int i = 0; i < keypad.length; i++) {
				origin = keypad[origin].snapOrigin;
				if (origin == SCREEN) {
					return true;
				}
			}
		}
		return false;
	}

	private void snapKey(int key, int level) {
		if (level >= snapStack.length) {
			Log.d(TAG, "Snap loop detected: ");
			for (int i = 1; i < snapStack.length; i++) {
				System.out.print(snapStack[i]);
				System.out.print(", ");
			}
			Log.d(TAG, String.valueOf(key));
			return;
		}
		snapStack[level] = key;
		VirtualKey vKey = keypad[key];
		if (vKey.snapOrigin == SCREEN) {
			RectSnap.snap(vKey.rect, screen, vKey.snapMode, vKey.snapOffset);
		} else {
			if (!keypad[vKey.snapOrigin].snapValid) {
				snapKey(vKey.snapOrigin, level + 1);
			}
			RectSnap.snap(vKey.rect, keypad[vKey.snapOrigin].rect,
					vKey.snapMode, vKey.snapOffset);
		}
		vKey.snapValid = true;
	}

	private void snapKeys() {
		obscuresVirtualScreen = false;
		for (int i = 0; i < keypad.length; i++) {
			snapKey(i, 0);
			VirtualKey key = keypad[i];
			RectF rect = key.rect;
			key.corners = (int) (Math.min(rect.width(), rect.height()) * 0.25F);
			if (RectF.intersects(rect, virtualScreen)) {
				if (key.visible) {
					obscuresVirtualScreen = true;
				}
				key.opaque = false;
			} else {
				key.opaque = settings.vkForceOpacity;
			}
		}
	}

	public boolean isPhone() {
		return layoutVariant == TYPE_PHONE || layoutVariant == TYPE_PHONE_ARROWS;
	}

	private void highlightGroup(int group) {
		for (VirtualKey aKeypad : keypad) {
			aKeypad.selected = false;
		}
		if (group >= 0) {
			for (int key = 0; key < keyScaleGroups[group].length; key++) {
				keypad[keyScaleGroups[group][key]].selected = true;
			}
		}
	}

	public int getLayoutEditMode() {
		return layoutEditMode;
	}

	public void setLayoutEditMode(int mode) {
		layoutEditMode = mode;
		editedIndex = -1;
		highlightGroup(-1);
		handler.removeCallbacks(this);
		visible = true;
		overlayView.postInvalidate();
		hide();
	}

	private void resizeKey(int key, float w, float h) {
		VirtualKey vKey = keypad[key];
		vKey.resize(w, h);
		vKey.snapValid = false;
	}

	private void resizeKeyGroup(int group) {
		float sizeX = keySize * keyScales[group * 2];
		float sizeY = keySize * keyScales[group * 2 + 1];
		for (int key = 0; key < keyScaleGroups[group].length; key++) {
			resizeKey(keyScaleGroups[group][key], sizeX, sizeY);
		}
	}

	@Override
	public void resize(RectF screen, float left, float top, float right, float bottom) {
		this.screen = screen;
		virtualScreen.set(left, top, right, bottom);
		snapRadius = keyScales[0];
		for (int i = 1; i < keyScales.length; i++) {
			if (keyScales[i] < snapRadius) {
				snapRadius = keyScales[i];
			}
		}

		float keySize = getKeySize(screen.width(), screen.height());
		snapRadius = keySize * snapRadius / 8;
		this.keySize = keySize;
		for (int group = 0; group < keyScaleGroups.length; group++) {
			resizeKeyGroup(group);
		}
		snapKeys();
		overlayView.postInvalidate();
		int delay = settings.vkHideDelay;
		if (delay > 0 && obscuresVirtualScreen && layoutEditMode == LAYOUT_EOF) {
			for (VirtualKey key : associatedKeys) {
				if (key != null) {
					return;
				}
			}
			handler.postDelayed(this, delay);
		}
		// Initialize joystick position if needed
		if (isJoystick()) {
			initJoystickPosition();
		}
	}

	private float getKeySize(float screenWidth, float screenHeight) {
		if (isPhone()) {
			return screenWidth / 6.0f;
		} else if (screenWidth > screenHeight) {
			return Math.min(screenWidth / 12.0f, screenHeight / 6.0f);
		} else {
			return Math.min(screenWidth / 6.0f, screenHeight / 12.0f);
		}
	}

	@Override
	public void paint(CanvasWrapper g) {
		if (visible && (layoutEditMode != LAYOUT_EOF || settings.vkAlpha > 0)) {
			for (int i = 0; i < keypad.length; i++) {
				VirtualKey key = keypad[i];
				if (key.visible) {
					key.paint(g);
				}
			}
			// Draw joystick
			if (isJoystick() && joystickVisible) {
				paintJoystick(g);
			}
		}
	}

	private void paintJoystick(CanvasWrapper g) {
		int alpha = (layoutEditMode != LAYOUT_EOF ? 0xFF : settings.vkAlpha) << 24;

		// Draw outer circle (base)
		g.setFillColor((layoutEditMode != LAYOUT_EOF ? (0xFF / 3) << 24 : alpha) | settings.vkBgColor);
		g.setDrawColor(alpha | settings.vkOutlineColor);
		g.fillArc(joystickRect, 0, 360);
		g.drawArc(joystickRect, 0, 360);

		// Draw direction indicators
		float indicatorSize = joystickRadius * 0.15f;
		int indicatorColor = alpha | (settings.vkFgColor & 0x00FFFFFF);
		g.setTextColor(indicatorColor);
		g.drawString(ARROW_UP, joystickCenterX, joystickCenterY - joystickRadius * 0.7f);
		g.drawString(ARROW_DOWN, joystickCenterX, joystickCenterY + joystickRadius * 0.7f);
		g.drawString(ARROW_LEFT, joystickCenterX - joystickRadius * 0.7f, joystickCenterY);
		g.drawString(ARROW_RIGHT, joystickCenterX + joystickRadius * 0.7f, joystickCenterY);

		// Draw dead zone circle
		float deadZone = joystickRadius * 0.25f;
		RectF deadRect = new RectF(
				joystickCenterX - deadZone,
				joystickCenterY - deadZone,
				joystickCenterX + deadZone,
				joystickCenterY + deadZone
		);
		g.setDrawColor((alpha & 0x44000000) | (settings.vkOutlineColor & 0x00FFFFFF));
		g.drawArc(deadRect, 0, 360);

		// Draw thumb
		int thumbColor;
		if (joystickActive) {
			thumbColor = (layoutEditMode != LAYOUT_EOF ? (0xFF / 3) << 24 : alpha) | settings.vkBgColorSelected;
		} else {
			thumbColor = (layoutEditMode != LAYOUT_EOF ? (0xFF / 3) << 24 : alpha) | settings.vkFgColor;
		}
		g.setFillColor(thumbColor);
		RectF thumbRect = new RectF(
				joystickThumbX - joystickThumbRadius,
				joystickThumbY - joystickThumbRadius,
				joystickThumbX + joystickThumbRadius,
				joystickThumbY + joystickThumbRadius
		);
		g.fillArc(thumbRect, 0, 360);
		g.setDrawColor(alpha | settings.vkOutlineColor);
		g.drawArc(thumbRect, 0, 360);

		// Draw resize handle in edit mode
		if (layoutEditMode == LAYOUT_KEYS || layoutEditMode == LAYOUT_SCALES) {
			float handleSize = joystickRadius * 0.2f;
			float hx = joystickCenterX + joystickRadius - handleSize;
			float hy = joystickCenterY + joystickRadius - handleSize;
			RectF handleRect = new RectF(hx, hy, hx + handleSize * 2, hy + handleSize * 2);
			g.setFillColor(0xAAFF8800);
			g.fillArc(handleRect, 0, 360);
			g.setDrawColor(0xFFFFFFFF);
			g.drawArc(handleRect, 0, 360);
		}
	}

	@Override
	public boolean pointerPressed(int pointer, float x, float y) {
		switch (layoutEditMode) {
			case LAYOUT_EOF -> {
				if (pointer > associatedKeys.length) {
					return false;
				}
				// Check joystick first
				if (isJoystick() && joystickVisible && joystickPointer < 0) {
					float dx = x - joystickCenterX;
					float dy = y - joystickCenterY;
					if (dx * dx + dy * dy <= joystickRadius * joystickRadius) {
						vibrate();
						joystickActive = true;
						joystickPointer = pointer;
						handleJoystickMove(x, y);
						overlayView.postInvalidate();
						return true;
					}
				}
				for (int i = 0; i < keypad.length; i++) {
					VirtualKey key = keypad[i];
					if (!key.visible) continue;
					if (key.contains(x, y)) {
						vibrate();
						associatedKeys[pointer] = key;
						key.onDown();
						overlayView.postInvalidate();
						break;
					}
				}
			}
			case LAYOUT_KEYS -> {
				// Check joystick drag/resize in edit mode
				if (isJoystick()) {
					float handleSize = joystickRadius * 0.2f;
					float hx = joystickCenterX + joystickRadius - handleSize;
					float hy = joystickCenterY + joystickRadius - handleSize;
					float hdx = x - (hx + handleSize);
					float hdy = y - (hy + handleSize);
					if (hdx * hdx + hdy * hdy <= (handleSize * 2) * (handleSize * 2)) {
						joystickResizeMode = true;
						joystickResizeStartDist = (float) Math.sqrt(
								(x - joystickCenterX) * (x - joystickCenterX) +
								(y - joystickCenterY) * (y - joystickCenterY));
						joystickResizeStartRadius = joystickRadius;
						return false;
					}
					float dx = x - joystickCenterX;
					float dy = y - joystickCenterY;
					if (dx * dx + dy * dy <= joystickRadius * joystickRadius) {
						joystickDragMode = true;
						joystickDragOffsetX = dx;
						joystickDragOffsetY = dy;
						return false;
					}
				}
				editedIndex = -1;
				for (int i = 0; i < keypad.length; i++) {
					if (keypad[i].contains(x, y)) {
						editedIndex = i;
						RectF rect = keypad[i].rect;
						offsetX = x - rect.left;
						offsetY = y - rect.top;
						break;
					}
				}
			}
			case LAYOUT_SCALES -> {
				// Check joystick resize in scale mode
				if (isJoystick()) {
					float dx = x - joystickCenterX;
					float dy = y - joystickCenterY;
					if (dx * dx + dy * dy <= joystickRadius * joystickRadius) {
						joystickResizeMode = true;
						joystickResizeStartDist = (float) Math.sqrt(dx * dx + dy * dy);
						joystickResizeStartRadius = joystickRadius;
						return false;
					}
				}
				int index = -1;
				for (int group = 0; group < keyScaleGroups.length && index < 0; group++) {
					for (int key = 0; key < keyScaleGroups[group].length && index < 0; key++) {
						if (keypad[keyScaleGroups[group][key]].contains(x, y)) {
							index = group;
						}
					}
				}
				if (editedIndex == index) {
					editedIndex = -1;
					highlightGroup(-1);
					overlayView.postInvalidate();
				} else if (index >= 0) {
					editedIndex = index;
					highlightGroup(index);
					overlayView.postInvalidate();
				}
				if (editedIndex >= 0) {
					prevScaleX = keyScales[editedIndex * 2];
					prevScaleY = keyScales[editedIndex * 2 + 1];
				}
				offsetX = x;
				offsetY = y;
			}
		}
		return false;
	}

	@Override
	public boolean pointerDragged(int pointer, float x, float y) {
		switch (layoutEditMode) {
			case LAYOUT_EOF -> {
				if (pointer > associatedKeys.length) {
					return false;
				}
				// Handle joystick drag
				if (isJoystick() && joystickActive && pointer == joystickPointer) {
					handleJoystickMove(x, y);
					overlayView.postInvalidate();
					return true;
				}
				VirtualKey aKey = associatedKeys[pointer];
				if (aKey == null) {
					pointerPressed(pointer, x, y);
				} else if (!aKey.contains(x, y)) {
					associatedKeys[pointer] = null;
					aKey.onUp();
					overlayView.postInvalidate();
					pointerPressed(pointer, x, y);
				}
			}
			case LAYOUT_KEYS -> {
				// Handle joystick drag/resize in edit mode
				if (joystickDragMode) {
					joystickCenterX = x - joystickDragOffsetX;
					joystickCenterY = y - joystickDragOffsetY;
					joystickThumbX = joystickCenterX;
					joystickThumbY = joystickCenterY;
					updateJoystickRect();
					overlayView.postInvalidate();
					return false;
				}
				if (joystickResizeMode) {
					float dist = (float) Math.sqrt(
							(x - joystickCenterX) * (x - joystickCenterX) +
							(y - joystickCenterY) * (y - joystickCenterY));
					float ratio = dist / joystickResizeStartDist;
					float newRadius = joystickResizeStartRadius * ratio;
					float minRadius = keySize * 0.5f;
					float maxRadius = Math.min(screen.width(), screen.height()) * 0.4f;
					joystickRadius = Math.max(minRadius, Math.min(maxRadius, newRadius));
					joystickThumbRadius = joystickRadius * 0.35f;
					joystickThumbX = joystickCenterX;
					joystickThumbY = joystickCenterY;
					updateJoystickRect();
					overlayView.postInvalidate();
					return false;
				}
				if (editedIndex >= 0) {
					VirtualKey key = keypad[editedIndex];
					RectF rect = key.rect;
					rect.offsetTo(x - offsetX, y - offsetY);
					key.snapMode = RectSnap.NO_SNAP;
					for (int i = 0; i < keypad.length; i++) {
						if (i != editedIndex && findSnap(editedIndex, i)) {
							break;
						}
					}
					if (key.snapMode == RectSnap.NO_SNAP) {
						key.snapMode = RectSnap.getSnap(rect, screen, key.snapOffset);
						key.snapOrigin = SCREEN;
						if (Math.abs(key.snapOffset.x) <= snapRadius) {
							key.snapOffset.x = 0;
						}
						if (Math.abs(key.snapOffset.y) <= snapRadius) {
							key.snapOffset.y = 0;
						}
					}
					snapKey(editedIndex, 0);
					overlayView.postInvalidate();
				}
			}
			case LAYOUT_SCALES -> {
				// Handle joystick resize in scale mode
				if (joystickResizeMode) {
					float dist = (float) Math.sqrt(
							(x - joystickCenterX) * (x - joystickCenterX) +
							(y - joystickCenterY) * (y - joystickCenterY));
					float ratio = dist / joystickResizeStartDist;
					float newRadius = joystickResizeStartRadius * ratio;
					float minRadius = keySize * 0.5f;
					float maxRadius = Math.min(screen.width(), screen.height()) * 0.4f;
					joystickRadius = Math.max(minRadius, Math.min(maxRadius, newRadius));
					joystickThumbRadius = joystickRadius * 0.35f;
					joystickThumbX = joystickCenterX;
					joystickThumbY = joystickCenterY;
					updateJoystickRect();
					overlayView.postInvalidate();
					return false;
				}
				if (editedIndex == -1) {
					break;
				}
				float dx = x - offsetX;
				float dy = offsetY - y;
				int index = editedIndex * 2;
				float scale = prevScaleX + dx / Math.min(screen.centerX(), screen.centerY());
				if (scale <= 0.0f) {
					scale = Float.MIN_VALUE;
				}
				if (Math.abs(1 - scale) <= SCALE_SNAP_RADIUS) {
					scale = 1;
				} else {
					for (int i = 0; i < keyScales.length; i += 2) {
						if (i != index && Math.abs(keyScales[i] - scale) <= SCALE_SNAP_RADIUS) {
							scale = keyScales[i];
							break;
						}
					}
				}
				keyScales[index++] = scale;
				scale = prevScaleY + dy / Math.min(screen.centerX(), screen.centerY());
				if (scale <= 0.0f) {
					scale = Float.MIN_VALUE;
				}
				if (Math.abs(1 - scale) <= SCALE_SNAP_RADIUS) {
					scale = 1;
				} else {
					for (int i = 1; i < keyScales.length; i += 2) {
						if (i != index && Math.abs(keyScales[i] - scale) <= SCALE_SNAP_RADIUS) {
							scale = keyScales[i];
							break;
						}
					}
				}
				keyScales[index] = scale;
				resizeKeyGroup(editedIndex);
				snapKeys();
				overlayView.postInvalidate();
			}
		}
		return false;
	}

	@Override
	public boolean pointerReleased(int pointer, float x, float y) {
		if (layoutEditMode == LAYOUT_EOF) {
			if (pointer > associatedKeys.length) {
				return false;
			}
			// Handle joystick release
			if (isJoystick() && joystickActive && pointer == joystickPointer) {
				releaseJoystick();
				overlayView.postInvalidate();
				return true;
			}
			VirtualKey key = associatedKeys[pointer];
			if (key != null) {
				associatedKeys[pointer] = null;
				key.onUp();
				overlayView.postInvalidate();
			}
		} else if (layoutEditMode == LAYOUT_KEYS) {
			if (joystickDragMode || joystickResizeMode) {
				joystickDragMode = false;
				joystickResizeMode = false;
				saveJoystickSettings();
				return false;
			}
			for (int key = 0; key < keypad.length; key++) {
				VirtualKey vKey = keypad[key];
				if (vKey.snapOrigin == editedIndex) {
					vKey.snapMode = RectSnap.NO_SNAP;
					for (int i = 0; i < KEYBOARD_SIZE; i++) {
						if (i != key && findSnap(key, i)) {
							break;
						}
					}
					if (vKey.snapMode == RectSnap.NO_SNAP) {
						vKey.snapMode = RectSnap.getSnap(vKey.rect, screen, vKey.snapOffset);
						vKey.snapOrigin = SCREEN;
						if (Math.abs(vKey.snapOffset.x) <= snapRadius) {
							vKey.snapOffset.x = 0;
						}
						if (Math.abs(vKey.snapOffset.y) <= snapRadius) {
							vKey.snapOffset.y = 0;
						}
					}
					snapKey(key, 0);
				}
			}
			snapKeys();
			editedIndex = -1;
		} else if (layoutEditMode == LAYOUT_SCALES) {
			if (joystickResizeMode) {
				joystickResizeMode = false;
				saveJoystickSettings();
			}
		}
		return false;
	}

	private void handleJoystickMove(float x, float y) {
		if (target == null) return;

		float dx = x - joystickCenterX;
		float dy = y - joystickCenterY;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);

		// Clamp to joystick boundary
		float maxDist = joystickRadius - joystickThumbRadius;
		if (dist > maxDist) {
			float ratio = maxDist / dist;
			dx *= ratio;
			dy *= ratio;
			dist = maxDist;
		}

		joystickThumbX = joystickCenterX + dx;
		joystickThumbY = joystickCenterY + dy;

		// Calculate normalized position
		float normX = dx / maxDist;
		float normY = dy / maxDist;

		// Dead zone threshold
		float deadZone = 0.25f;

		boolean newUp = normY < -deadZone;
		boolean newDown = normY > deadZone;
		boolean newLeft = normX < -deadZone;
		boolean newRight = normX > deadZone;

		// Press/release keys based on changes
		if (newUp && !jsUp) {
			target.postKeyPressed(joystickKeyUp);
		} else if (!newUp && jsUp) {
			target.postKeyReleased(joystickKeyUp);
		}

		if (newDown && !jsDown) {
			target.postKeyPressed(joystickKeyDown);
		} else if (!newDown && jsDown) {
			target.postKeyReleased(joystickKeyDown);
		}

		if (newLeft && !jsLeft) {
			target.postKeyPressed(joystickKeyLeft);
		} else if (!newLeft && jsLeft) {
			target.postKeyReleased(joystickKeyLeft);
		}

		if (newRight && !jsRight) {
			target.postKeyPressed(joystickKeyRight);
		} else if (!newRight && jsRight) {
			target.postKeyReleased(joystickKeyRight);
		}

		jsUp = newUp;
		jsDown = newDown;
		jsLeft = newLeft;
		jsRight = newRight;
	}

	private void releaseJoystick() {
		if (target != null) {
			if (jsUp) target.postKeyReleased(joystickKeyUp);
			if (jsDown) target.postKeyReleased(joystickKeyDown);
			if (jsLeft) target.postKeyReleased(joystickKeyLeft);
			if (jsRight) target.postKeyReleased(joystickKeyRight);
		}
		jsUp = jsDown = jsLeft = jsRight = false;
		joystickActive = false;
		joystickPointer = -1;
		joystickThumbX = joystickCenterX;
		joystickThumbY = joystickCenterY;
	}

	@Override
	public void show() {
		if (settings.vkHideDelay > 0 && obscuresVirtualScreen) {
			handler.removeCallbacks(this);
			if (!visible) {
				visible = true;
				overlayView.postInvalidate();
			}
		}
	}

	@Override
	public void hide() {
		long delay = settings.vkHideDelay;
		if (delay > 0 && obscuresVirtualScreen && layoutEditMode == LAYOUT_EOF) {
			handler.postDelayed(this, delay);
		}
	}

	@Override
	public void cancel() {
		for (VirtualKey key : keypad) {
			key.selected = false;
			handler.removeCallbacks(key);
		}
		// Cancel joystick
		if (joystickActive) {
			releaseJoystick();
		}
	}

	@Override
	public void run() {
		visible = false;
		overlayView.postInvalidate();
	}

	@Override
	public boolean keyPressed(int keyCode) {
		int hashCode = 31 * (31 + keyCode);
		for (VirtualKey key : keypad) {
			if (key.hashCode() == hashCode) {
				key.selected = true;
				overlayView.postInvalidate();
				break;
			}
		}
		return false;
	}

	@Override
	public boolean keyRepeated(int keyCode) {
		return false;
	}

	@Override
	public boolean keyReleased(int keyCode) {
		int hashCode = 31 * (31 + keyCode);
		for (VirtualKey key : keypad) {
			if (key.hashCode() == hashCode) {
				key.selected = false;
				overlayView.postInvalidate();
				break;
			}
		}
		return false;
	}

	private void vibrate() {
		if (settings.vkFeedback) ContextHolder.vibrateKey(FEEDBACK_DURATION);
	}

	public void setView(View view) {
		overlayView = view;
	}

	public int getKeyStatesVodafone() {
		int keyStates = 0;
		for (int i = 0; i < keypad.length; i++) {
			VirtualKey key = keypad[i];
			if (key.selected) {
				keyStates |= getKeyBit(i);
			}
		}
		return keyStates;
	}

	private int getKeyBit(int vKey) {
		return switch (vKey) {
			case KEY_NUM0       -> 1      ; //  0 0
			case KEY_NUM1       -> 1 <<  1; //  1 1
			case KEY_NUM2       -> 1 <<  2; //  2 2
			case KEY_NUM3       -> 1 <<  3; //  3 3
			case KEY_NUM4       -> 1 <<  4; //  4 4
			case KEY_NUM5       -> 1 <<  5; //  5 5
			case KEY_NUM6       -> 1 <<  6; //  6 6
			case KEY_NUM7       -> 1 <<  7; //  7 7
			case KEY_NUM8       -> 1 <<  8; //  8 8
			case KEY_NUM9       -> 1 <<  9; //  9 9
			case KEY_STAR       -> 1 << 10; // 10 *
			case KEY_POUND      -> 1 << 11; // 11 #
			case KEY_UP         -> 1 << 12; // 12 Up
			case KEY_LEFT       -> 1 << 13; // 13 Left
			case KEY_RIGHT      -> 1 << 14; // 14 Right
			case KEY_DOWN       -> 1 << 15; // 15 Down
			case KEY_FIRE       -> 1 << 16; // 16 Select
			case KEY_SOFT_LEFT  -> 1 << 17; // 17 Softkey 1
			case KEY_SOFT_RIGHT -> 1 << 18; // 18 Softkey 2
			// TODO: 05.08.2020 Softkey3 mapped to KEY_C
			case KEY_C          -> 1 << 19; // 19 Softkey 3
			case KEY_UP_RIGHT   -> 1 << 20; // 20 Upper Right
			case KEY_UP_LEFT    -> 1 << 21; // 21 Upper Left
			case KEY_DOWN_RIGHT -> 1 << 22; // 22 Lower Right
			case KEY_DOWN_LEFT  -> 1 << 23; // 23 Lower Left
			default             -> 0      ;
		};
	}

	public void saveScreenParams() {
		float scale = virtualScreen.width() / screen.width();
		settings.screenScaleRatio = Math.round(scale * 100);
		settings.screenGravity = 1;
		ProfilesManager.saveConfig(settings);
	}

	private class VirtualKey implements Runnable {
		final String label;
		final int keyCode;
		final RectF rect = new RectF();
		final PointF snapOffset = new PointF();
		int snapOrigin;
		int snapMode;
		boolean snapValid;
		boolean selected;
		boolean visible = true;
		boolean opaque = true;
		int corners;
		private final int hashCode;
		private int repeatCount;

		VirtualKey(int keyCode, String label) {
			this.keyCode = keyCode;
			this.label = label;
			hashCode = 31 * (31 + this.keyCode);
		}

		void resize(float width, float height) {
			rect.right = rect.left + width;
			rect.bottom = rect.top + height;
		}

		boolean contains(float x, float y) {
			return visible && rect.contains(x, y);
		}

		void paint(CanvasWrapper g) {
			int bgColor;
			int fgColor;
			if (selected) {
				bgColor = settings.vkBgColorSelected;
				fgColor = settings.vkFgColorSelected;
			} else {
				bgColor = settings.vkBgColor;
				fgColor = settings.vkFgColor;
			}
			int alpha = (opaque || layoutEditMode != LAYOUT_EOF ? 0xFF : settings.vkAlpha) << 24;
			g.setFillColor((layoutEditMode != LAYOUT_EOF ? (0xFF / 3) << 24 : alpha) | bgColor);
			g.setTextColor(alpha | fgColor);
			g.setDrawColor(alpha | settings.vkOutlineColor);

			switch (settings.vkButtonShape) {
				case SHAPE_ROUND_RECT -> {
					g.fillRoundRect(rect, corners, corners);
					g.drawRoundRect(rect, corners, corners);
				}
				case SHAPE_RECT -> {
					g.fillRect(rect);
					g.drawRect(rect);
				}
				case SHAPE_OVAL -> {
					g.fillArc(rect, 0, 360);
					g.drawArc(rect, 0, 360);
				}
			}
			g.drawString(label, rect.centerX(), rect.centerY());
		}

		@NonNull
		public String toString() {
			return "[" + label + ": " + rect.left + ", " + rect.top + ", " + rect.right + ", " + rect.bottom + "]";
		}

		public int hashCode() {
			return hashCode;
		}

		@Override
		public void run() {
			if (target == null) {
				selected = false;
				repeatCount = 0;
				return;
			}
			if (selected) {
				onRepeat();
			} else {
				repeatCount = 0;
			}
		}

		public void onRepeat() {
			handler.postDelayed(this, repeatCount > 6 ? 80 : REPEAT_INTERVALS[repeatCount++]);
			target.postKeyRepeated(keyCode);
		}

		protected void onDown() {
			selected = true;
			target.postKeyPressed(keyCode);
			handler.postDelayed(this, 400);
		}

		public void onUp() {
			selected = false;
			handler.removeCallbacks(this);
			target.postKeyReleased(keyCode);
		}
	}

	private class DualKey extends VirtualKey {

		final int secondKeyCode;
		private final int hashCode;

		DualKey(int keyCode, int secondKeyCode, String label) {
			super(keyCode, label);
			if (secondKeyCode == 0) throw new IllegalArgumentException();
			this.secondKeyCode = secondKeyCode;
			hashCode = 31 * (31 + this.keyCode) + this.secondKeyCode;
		}

		@Override
		protected void onDown() {
			super.onDown();
			target.postKeyPressed(secondKeyCode);
		}

		@Override
		public void onUp() {
			super.onUp();
			target.postKeyReleased(secondKeyCode);
		}

		@Override
		public void onRepeat() {
			super.onRepeat();
			target.postKeyRepeated(secondKeyCode);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}
	}

	private class MenuKey extends VirtualKey {

		MenuKey() {
			super(KeyMapper.KEY_OPTIONS_MENU, "M");
		}

		@Override
		protected void onDown() {
			selected = true;
			handler.postDelayed(this, 500);
		}

		@Override
		public void onUp() {
			if (selected) {
				selected = false;
				handler.removeCallbacks(this);
				MicroActivity activity = ContextHolder.getActivity();
				if (activity != null) {
					activity.openOptionsMenu();
				}
			}
		}

		@Override
		public void run() {
			selected = false;
			MicroActivity activity = ContextHolder.getActivity();
			if (activity != null) {
				activity.runOnUiThread(activity::showExitConfirmation);
			}
		}
	}
}
