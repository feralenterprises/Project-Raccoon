package com.feralenterprises.raccoon.ui.theme;

/**
 * Shared geometry tokens. Every component takes spacing and sizes from here so one change applies everywhere.
 * Values marked PROPOSED still need owner approval and native RuneLite verification.
 */
public final class Tokens {
	private Tokens() { }

	/** Primary content viewport width in logical pixels (excludes rail, frame chrome and scrollbar). */
	public static final int CONTENT_WIDTH = 225;
	/** PROPOSED alternative content widths. */
	public static final int[] CONTENT_WIDTHS = {225, 263, 280};

	/**
	 * PROPOSED rail width: the button column only. UI-003 owner correction removed the 4 px rail scrollbar track and the
	 * separator between rail and content; the rail still scrolls (wheel, keyboard focus) with a zero-width bar.
	 */
	public static final int RAIL_WIDTH = 34;
	public static final int RAIL_SCROLLBAR = 0;
	public static final int RAIL_BUTTON_HEIGHT = 30;
	/** Outer frame chrome on the content column's outer sides (none on the rail side while the rail is shown). */
	public static final int CHROME = 3;
	/** Content scrollbar track; always reserved so the viewport never changes width. */
	public static final int SCROLLBAR = 6;

	public static final int INSET = 8;
	public static final int GAP = 6;
	public static final int GAP_SMALL = 4;
	public static final int PAD_X = 8;
	public static final int PAD_Y = 4;

	/** Stone frame: source slice 24 px, destination corner 16 px (kit guidance), content inset 8 px. */
	public static final int FRAME_SLICE = 24;
	public static final int FRAME_CORNER = 16;
	public static final int FRAME_INSET = 8;

	/** Native item icon size and slot pitches (OSRS equipment and inventory arrangements). */
	public static final int ITEM_W = 36;
	public static final int ITEM_H = 32;
	public static final int EQUIP_SLOT = 36;
	public static final int EQUIP_PITCH_X = 41;
	public static final int EQUIP_PITCH_Y = 39;
	public static final int INV_PITCH_X = 42;
	public static final int INV_PITCH_Y = 36;

	public static final int PROGRESS_HEIGHT = 10;
	public static final int FOOTER_ART_MAX = 127;
}
