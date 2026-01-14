/*
	Copyright (C) 2026 Kumohakase403 CC-BY-SA 4.0
	Please support me: https://www.patreon.com/c/kumohakasemk8, https://ko-fi.com/kumohakase
	No support nor warranty
*/

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.sound.sampled.*;
import javax.imageio.*;
import java.io.*;

public class MainWindow extends JFrame implements ActionListener, MouseMotionListener, KeyListener {
	public static void main(String[] args) {
		new MainWindow();
	}
	BufferedImage ScrBuf;
	Graphics2D GScr;
	JPanel PPanel;
	Mixer MAudioIn;
	TargetDataLine LAudioIn;
	int AudioLevel;
	byte AudioBuffer[];
	int AudioBufferCur;
	int VTMinAudioLevel;
	int VTMaxAudioLevel;
	double VTMVRatio;
	int VTEXRange, VTEYRange;
	int MouseX, MouseY;
	Robot JRobot;
	BufferedImage ScrCap;
	int ScrCapX, ScrCapY, ScrCapW, ScrCapH;
	int GMouseX, GMouseY;
	int VTBodyID, VTEyeID, VTMouthIDA, VTMouthIDB;
	ImageElements ELEMs[];
	String CmdBuf;
	int CmdBufCur;
	int DebugMode;
	int MoveMode;
	int ResizeMode;
	int VTMoveMode[];
	int VTResizeMode[];
	String StatusBuf;
	int StatusRemain;
	int ScrW;
	int ScrH;
	public MainWindow() {
		super("Broadcast window");
		try {
			JRobot = new Robot();
		} catch (AWTException e) {
			JRobot = null;
			System.out.println("Error creating Robot class, screen capture disabled.");
		}
		ScrW = 800;
		ScrH = 600;
		VTMoveMode = null;
		VTResizeMode = null;
		DebugMode = 0;
		StatusRemain = 0;
		MoveMode = -1;
		ResizeMode = -1;
		CmdBuf = null;
		VTBodyID = -1;
		VTEyeID = -1;
		VTMouthIDA = -1;
		VTMouthIDB = -1;
		ScrCapX = 100;
		ScrCapY = 100;
		ScrCapW = 800;
		ScrCapH = 600;
		VTMinAudioLevel = 0;
		VTMaxAudioLevel = 128;
		VTMVRatio = 2.0;
		VTEXRange = 10;
		VTEYRange = 10;
		MouseX = 0;
		MouseY = 0;
		ELEMs = new ImageElements[0];
		try {
			BufferedReader br = new BufferedReader(new FileReader("settings.ini") );
			String sec = "";
			int lineno = 0;
			while(true) {
				String line = br.readLine();
				if(line == null) { break; }
				lineno++;
				line = line.strip();
				if(line.equals("") ) { continue; }
				if(line.startsWith("[") && line.endsWith("]") ) {
					sec = line;
				} else if(sec.equals("[Images]") ) {
					String[] s = divideString(line, '=');
					int imx = 10, imy = 10, imw = 0, imh = 0;
					if(s != null) {
						String[] poss = s[0].split(",");
						if(poss.length == 4) {
							try {
								imx = Integer.parseInt(poss[0]);
								imy = Integer.parseInt(poss[1]);
								imw = Integer.parseInt(poss[2]);
								imh = Integer.parseInt(poss[3]);
							} catch(NumberFormatException ex2) {
								System.out.printf("Image coordinate or ratio parse failed: %s at line %d\n", ex2.getMessage(), lineno);
							}
						}
					}
					String ifn = line;
					if(s != null) {
						ifn = s[1];
					}
					try {
						loadNewImg(ifn, imx, imy, imw, imh);
					} catch(IOException ex2) {
						System.out.printf("Image loading failed: %s\n", ex2.getMessage() );
					}
				} else if(sec.equals("[Settings]") ) {
					try {
						setSetting(line);
					} catch(IllegalArgumentException ex2) {
						System.out.printf("Error at line %d: %s\n", lineno, ex2.getMessage() );
					}
				}
			}
			br.close();
		} catch(IOException ex) {
			System.out.println("Failed to read settings.ini");
		}
		MAudioIn = null;
		LAudioIn = null;
		AudioLevel = 0;
		AudioBuffer = new byte[80];
		AudioBufferCur = 0;
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
		ScrBuf = new BufferedImage(ScrW, ScrH, BufferedImage.TYPE_INT_ARGB);
		GScr = ScrBuf.createGraphics();
		GScr.setFont( new Font("Ubuntu Mono", Font.PLAIN, 18) );
		GScr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		PPanel = new JPanel() {
			public void paint(Graphics g) {
				g.drawImage(ScrBuf, 0, 0, this);
			}
		};
		PPanel.setPreferredSize(new Dimension(ScrBuf.getWidth(), ScrBuf.getHeight() ) );
		add(PPanel);
		PPanel.addMouseMotionListener(this);
		addKeyListener(this);
		pack();
		AudioFormat afm = new AudioFormat(8000, 8, 1, false, false);
		DataLine.Info ifo = new DataLine.Info(TargetDataLine.class, afm);
		for(Mixer.Info i : AudioSystem.getMixerInfo() ) {
			Mixer mt = AudioSystem.getMixer(i);
			if(mt.isLineSupported(ifo) ) {
				MAudioIn = AudioSystem.getMixer(i);
				break;
			}
		}
		if(MAudioIn != null) {
			try {
				LAudioIn = (TargetDataLine)MAudioIn.getLine(ifo);
				LAudioIn.open(afm);
				LAudioIn.start();
			} catch(LineUnavailableException ex) {
				System.out.println("Can not open audio device for input.\n");
			}
		} else {
			System.out.println("Can not find audio device.\n");
		}
		javax.swing.Timer t1 = new javax.swing.Timer(30, this);
		t1.setActionCommand("DRAW");
		t1.start();
		javax.swing.Timer t2 = new javax.swing.Timer(10, this);
		t2.setActionCommand("TICK");
		t2.start();
	}

	public void setSetting(String line) throws IllegalArgumentException {
		String[] t = divideString(line, '=');
		if(t == null) {
			throw new IllegalArgumentException("Not in Item=val form.");
		}
		String item = t[0];
		String val = t[1];
		System.out.printf("[%s] %s\n", item, val);
		if(item.equals("VTMinAudioLevel") ) {
			VTMinAudioLevel = Integer.parseInt(val);
		} else if(item.equals("VTMaxAudioLevel") ) {
			VTMaxAudioLevel = Integer.parseInt(val);
		} else if(item.equals("VTMVRatio") ) {
			VTMVRatio = Double.parseDouble(val);
		} else if(item.equals("ScrCapX") ) {
			ScrCapX = rangeparseInt(val, 0, 20000);
		} else if(item.equals("ScrCapY") ) {
			ScrCapY = rangeparseInt(val, 0, 20000);
		} else if(item.equals("ScrCapW") ) {
			ScrCapW = rangeparseInt(val, 0, 20000);
		} else if(item.equals("ScrCapH") ) {
			ScrCapH = rangeparseInt(val, 0, 20000);
		} else if(item.equals("VTBodyID") ) {
			VTBodyID = Integer.parseInt(val);
		} else if(item.equals("VTEyeID") ) {
			VTEyeID = Integer.parseInt(val);
		} else if(item.equals("VTMouthIDA") ) {
			VTMouthIDA = Integer.parseInt(val);
		} else if(item.equals("VTMouthIDB") ) {
			VTMouthIDB = Integer.parseInt(val);
		} else if(item.equals("VTEXRange") ) {
			VTEXRange = Integer.parseInt(val);
		} else if(item.equals("VTEYRange") ) {
			VTEYRange = Integer.parseInt(val);
		} else if(item.equals("ScrW") ) {
			ScrW = rangeparseInt(val, 10, 30000);
		} else if(item.equals("ScrH") ) {
			ScrH = rangeparseInt(val, 10, 30000);
		} else {
			throw new IllegalArgumentException( String.format("Unknown setting key: %s", item) );
		}
	}

	public String[] divideString(String line, char mark) {
		int eqpos = line.indexOf(mark);
		if(eqpos == -1) {
			return null;
		}
		String setname = line.substring(0, eqpos).strip();
		String setval = line.substring(eqpos + 1).strip();
		String r[] = new String[2];
		r[0] = setname;
		r[1] = setval;
		return r;
	}

	public double rangeparseDouble(String v, double mi, double mx) throws IllegalArgumentException {
		double t;
		try {
			t = Double.parseDouble(v);
		} catch(NumberFormatException ex) {
			throw new IllegalArgumentException("Illegal input.");
		}
		if(mi <= t && t <= mx) {
			return t;
		} else {
			throw new IllegalArgumentException(String.format("Must be in range %.2f - %.2f", mi, mx) );
		}
	}

	public int rangeparseInt(String v, int mi, int mx) throws IllegalArgumentException {
		int t;
		try {
			t = Integer.parseInt(v);
		} catch(NumberFormatException ex) {
			throw new IllegalArgumentException("Illegal input.");
		}
		if(mi <= t && t <= mx) {
			return t;
		} else {
			throw new IllegalArgumentException(String.format("Must be in range %d - %d", mi, mx) );
		}
	}


	public void mouseDragged(MouseEvent e) {
		MouseX = (int)e.getX();
		MouseY = (int)e.getY();
	}

	public void mouseMoved(MouseEvent e) {
		MouseX = (int)e.getX();
		MouseY = (int)e.getY();
	}

	public void keyPressed(KeyEvent e) {
		if(MoveMode != -1 || ResizeMode != -1) {return;}
		if(CmdBuf != null) {
			if(e.getKeyCode() == KeyEvent.VK_LEFT) {
				if(CmdBufCur > 0) { CmdBufCur--; }
			} else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
				if(CmdBufCur < CmdBuf.length() ) { CmdBufCur++; }
			}
		}
	}

	public void keyReleased(KeyEvent e) {

	}

	public void keyTyped(KeyEvent e) {
		//System.out.println(Character.isLetter(e.getKeyChar() ) );
		if(MoveMode != -1 || ResizeMode != -1) {
			if(e.getKeyChar() == ' ') {
				MoveMode = -1;
				ResizeMode = -1;
				VTMoveMode = null;
				VTResizeMode = null;
			}
			return;
		}
		if(CmdBuf == null) {
			if(e.getKeyChar() == ':') {
				CmdBuf = ":"; 
				CmdBufCur = 1;
			} else if(e.getKeyChar() == 'd') {
				if(DebugMode < 3) { DebugMode++; } else { DebugMode = 0; }
			} else if(e.getKeyChar() == 'm') {
				if(isIDValid(VTBodyID) ) {
					MoveMode = VTBodyID;
					int IDS[] = {VTEyeID, VTMouthIDA, VTMouthIDB};
					VTMoveMode = new int[6];
					for(int i = 0; i < 3; i++) {
						if(isIDValid(i) ) {
							VTMoveMode[i * 2] = ELEMs[i].x - ELEMs[VTBodyID].x;
							VTMoveMode[i * 2 + 1] = ELEMs[i].y - ELEMs[VTBodyID].y;
						}
					}
				} else {
					System.out.println("VTBodyID is not set.");
				}
			} /*else if(e.getKeyChar() == 'r') {
				if(isIDValid(VTBodyID) ) {
					ResizeMode = VTBodyID;
					int IDS[] = {VTEyeID, VTMouthIDA, VTMouthIDB};
					VTResizeMode = new int[6];
					for(int i = 0; i < 3; i++) {
						if(isIDValid(i) ) {
							VTResizeMode[i * 2] = ELEMs[i].w;
							VTResizeMode[i * 2 + 1] = ELEMs[i].h;
						}
					}
				} else {
					System.out.println("VTBodyID is not set.");
				}
			}*/
		} else {
			if(!Character.isISOControl(e.getKeyChar() ) ) {
				CmdBuf = CmdBuf.substring(0, CmdBufCur) + e.getKeyChar() + CmdBuf.substring(CmdBufCur);
				CmdBufCur++;
			} else if(e.getKeyChar() == '\n') {
				execcmd(CmdBuf);
				CmdBuf = null;
			} else if(e.getKeyChar() == '\b') {
				if(CmdBufCur > 0) {
					CmdBuf = CmdBuf.substring(0, CmdBufCur - 1) + CmdBuf.substring(CmdBufCur);
					CmdBufCur--;
				} else {
					CmdBuf = null;
				}
			} else if(e.getKeyChar() == '\033') {
				CmdBuf = null;
			}
		}
	}

	public int loadNewImg(String fn, int x, int y, int w, int h) throws IOException {
		int r = -1;
		for(int i = 0; i < ELEMs.length; i++) {
			if(ELEMs[i] == null) {
				r = i;
				break;
			}
		}
		if(r == -1) {
			ImageElements tt[] = ELEMs.clone();
			ELEMs = new ImageElements[tt.length + 1];
			System.arraycopy(tt, 0, ELEMs, 0, tt.length);
			r = tt.length;
		}
		ELEMs[r] = new ImageElements(fn, x, y, w, h);
		System.out.printf("[%d] Loading image %s (+%d,%d %dx%d)\n", r, ELEMs[r].fn, ELEMs[r].x, ELEMs[r].y, ELEMs[r].w, ELEMs[r].h);
		return r;
	}

	int parseItemID(String i) throws IllegalArgumentException {
		int t = Integer.parseInt(i);
		if(!isIDValid(t) ) {
			throw new IllegalArgumentException("Bad ID!");
		}
		return t;
	}

	void statusUpd(String s) {
		StatusBuf = s;
		StatusRemain = 500;
	}
	void execcmd(String c) {
		String cs[] = c.split(" ");
		try {
			String t[] = divideString(c, ' ');
			String cmd, param;
			if(t == null) {
				cmd = c;
				param = null;
			} else {
				cmd = t[0];
				param = t[1];
			}
			if(cmd.equals(":set") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				setSetting(param);
			} else if(cmd.equals(":move") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				MoveMode = parseItemID(param);
			} else if(cmd.equals(":resize") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				ResizeMode = parseItemID(param);
			} else if(cmd.equals(":load") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				int i = loadNewImg(param, 10, 10, 0, 0);
			} else if(cmd.equals(":query") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				int i = parseItemID(param);
				statusUpd(ELEMs[i].fn);
				System.out.printf("Image ID%d: %s\n", i, ELEMs[i].fn);
			} else if(cmd.equals(":resetsize") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				int i = parseItemID(param);
				ELEMs[i].w = ELEMs[i].img.getWidth();
				ELEMs[i].h = ELEMs[i].img.getHeight();
			} else if(cmd.equals(":toggle") ) {
				if(param == null) {
					System.out.println("Option required.");
					statusUpd("Option required");
					return;
				}
				int i = parseItemID(param);
				if(ELEMs[i].v) {
					ELEMs[i].v = false;
				} else {
					ELEMs[i].v = true;
				}
			} else if(cmd.equals(":saveopt") ) {
				saveopt();
				statusUpd("Saved config");
				System.out.println("Saved config");
			} else {
				statusUpd("Not implemented.");
				System.out.println("Not implemented.");
			}
		} catch(IllegalArgumentException e) {
			statusUpd("Bad param");
			System.out.printf("Command parse error: %s\n", e.getMessage() );
		} catch(IOException e) {
			statusUpd("IO Error");
			System.out.printf("Command IO error: %s\n", e.getMessage() );
		}
	}

	public void saveopt() throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter("settings.ini") );
		bw.write("[Images]\n");
		for(ImageElements i : ELEMs) {
			if(i == null) { continue; }
			bw.write(String.format("%d,%d,%d,%d=%s\n", i.x, i.y, i.w, i.h, i.fn) );
		}
		bw.newLine();
		bw.write("[Settings]\n");
		bw.write(String.format("VTMinAudioLevel = %d\n", VTMinAudioLevel) );
		bw.write(String.format("VTMaxAudioLevel = %d\n", VTMaxAudioLevel) );
		bw.write(String.format("VTMVRatio = %f\n", VTMVRatio) );
		bw.write(String.format("ScrCapX = %d\n", ScrCapX) );
		bw.write(String.format("ScrCapY = %d\n", ScrCapY) );
		bw.write(String.format("ScrCapW = %d\n", ScrCapW) );
		bw.write(String.format("ScrCapH = %d\n", ScrCapH) );
		bw.write(String.format("VTBodyID = %d\n", VTBodyID) );
		bw.write(String.format("VTEyeID = %d\n", VTEyeID) );
		bw.write(String.format("VTMouthIDA = %d\n", VTMouthIDA) );
		bw.write(String.format("VTMouthIDB = %d\n", VTMouthIDB) );
		bw.write(String.format("VTEXRange = %d\n", VTEXRange) );
		bw.write(String.format("VTEYRange = %d\n", VTEYRange) );
		bw.write(String.format("ScrW = %d\n", ScrW) );
		bw.write(String.format("ScrH = %d\n", ScrH) );
		bw.close();
	}

	public boolean isIDValid(int i) {
		if(0 <= i && i <= ELEMs.length - 1 && ELEMs[i] != null) {
			return true;
		}
		return false;
	}

	public void drawImgID(int i) {
		drawImgIDAsRatio(i, 0, 0, 1, 1);
	}
	public void drawImgIDAsRatio(int i, int xoff, int yoff, double rw, double rh) {
		if(!isIDValid(i) ) {
			return;
		}
		if(ELEMs[i].v == false) {
			return;
		}
		BufferedImage im = ELEMs[i].img;
		if(im == null) { return; }
		int x = ELEMs[i].x + xoff;
		int y = ELEMs[i].y + yoff;
		int w = (int)(ELEMs[i].w * rw);
		int h = (int)(ELEMs[i].h * rh);
		if(DebugMode == 3) {
			GScr.setColor(Color.green);
			GScr.drawRect(x, y, w, h);
			GScr.drawString(String.format("[%d] %s", i, ELEMs[i].fn), x, y + 20);
			GScr.drawString(String.format("+%d, %d %dx%d", ELEMs[i].x, ELEMs[i].y, ELEMs[i].w, ELEMs[i].h), x, y + 40);
		}
		GScr.drawImage(im, x, y, w, h, this);
	}

	public void draw() {
		if(ScrCap != null) {
			GScr.drawImage(ScrCap, 0, 0, this);
		} else {
			GScr.setColor(Color.black);
			GScr.fillRect(0, 0, ScrBuf.getWidth(), ScrBuf.getHeight() );
		}
		GScr.setColor(Color.white);
		for(int i = 0; i < ELEMs.length; i++) {
			if(i != VTMouthIDA && i != VTMouthIDB && i != VTEyeID && i != VTBodyID) {
				drawImgID(i);
			}
		}
		drawImgID(VTBodyID);
		double mvr = (double)AudioLevel - (double)VTMinAudioLevel;
		if(mvr < 0) { 
			mvr = 0;
		} else {
			mvr = (mvr / ( (double)VTMaxAudioLevel - (double)VTMinAudioLevel) ) * VTMVRatio;
			if(mvr > VTMVRatio) {
				mvr = VTMVRatio;
			}
		}
		if(isIDValid(VTMouthIDB) && mvr == 0) {
			drawImgID(VTMouthIDB);
		} else if(isIDValid(VTMouthIDA) ) {
			drawImgIDAsRatio(VTMouthIDA, 0, 0, 1.0, mvr + 1.0);
		}
		int evx, evy;
		if(GMouseX < ScrCapX) {
			evx = 0;
		} else if(GMouseX > ScrCapX + ScrCapW) {
			evx = VTEXRange;
		} else {
			evx = (int) ( ( (double)(GMouseX - ScrCapX) / (double)ScrCapW) * VTEXRange);
		}
		if(GMouseY < ScrCapY) {
			evy = 0;
		} else if(GMouseY > ScrCapY + ScrCapH) {
			evy = VTEYRange;
		} else {
			evy = (int) ( ( (double)(GMouseY - ScrCapY) / (double)ScrCapH) * VTEYRange);
		}
		drawImgIDAsRatio(VTEyeID, evx, evy, 1.0, 1.0);
		int hobj = -1;
		if(isIDValid(MoveMode) ) {
			hobj = MoveMode;
		}
		if(isIDValid(ResizeMode) ) {
			hobj = ResizeMode;
		}
		if(hobj != -1) {
			GScr.setColor(new Color(0x7f00ff00, true) );
			BufferedImage _im = ELEMs[hobj].img;
			int _x = ELEMs[hobj].x;
			int _y = ELEMs[hobj].y;
			int _w = ELEMs[hobj].w;
			int _h = ELEMs[hobj].h;
			if(_w < 0) {
				_x += _w;
				_w = -_w;
			}
			if(_h < 0) {
				_y += _h;
				_h = -_h;
			}
			GScr.fillRect(_x, _y, _w, _h);
		}
		FontMetrics fm = GScr.getFontMetrics();
		int fh = fm.getMaxAscent() + fm.getMaxDecent();
		if(DebugMode == 1) {
			GScr.setColor(new Color(0x7f000000, true) );
			GScr.fillRect(0, 0, 320, 50 + fh * 5);
			GScr.setColor(Color.green);
			GScr.fillRect(10, 10, (int)(300.0 * ( (double)AudioLevel / 128.0) ), 10);
			GScr.drawRect(10, 10, 300, 10);
			GScr.drawString(String.format("AudioLevelRaw: %d", AudioLevel), 10, 25 + fh);
			GScr.drawString(String.format("AppliedMVRatio: %.3f", mvr), 10, 25 + fh * 2);
			GScr.drawString(String.format("Mouse: %dx%d (%dx%d)", MouseX, MouseY, GMouseX, GMouseY), 10, 25 + fh * 3);
			GScr.drawString(String.format("AppliedEyeOffset: +%d,%d", evx, evy), 10, 25 + fh * 4);
		} else if(DebugMode == 2) {
			GScr.setColor(new Color(0x7f000000, true) );
			GScr.fillRect(0, 0, 320, fh * 5 + 50);
			GScr.setColor(Color.green);
			GScr.drawString(String.format("VTAudioLevels: %d, %d", VTMinAudioLevel, VTMaxAudioLevel), 10, fh);
			GScr.drawString(String.format("VTMVRatio: %.2f", VTMVRatio), 10, fh * 2);
			GScr.drawString(String.format("ScrCap: +%d, %d %d x %d", ScrCapX, ScrCapY, ScrCapW, ScrCapH), 10, fh * 3);
			GScr.drawString(String.format("VTERanges: %d, %d", VTEXRange, VTEYRange), 10, fh * 4);
			GScr.drawString(String.format("VTIDs = %d, %d, %d, %d", VTBodyID, VTEyeID, VTMouthIDA, VTMouthIDB), 10, fh * 5);
		}
		if(CmdBuf != null || StatusRemain != 0) {
			GScr.setColor(Color.black);
			GScr.fillRect(0, ScrBuf.getHeight() - fh, ScrBuf.getWidth(), fh);
			GScr.setColor(Color.green);
		}
		if(CmdBuf != null) {
			int cw = fm.stringWidth(CmdBuf.substring(0, CmdBufCur) );
			int csp = 0;
			while(cw >= ScrBuf.getWidth() - 10 && csp < CmdBufCur) {
				csp++;
				cw = fm.stringWidth(CmdBuf.substring(csp, CmdBufCur) );
			}
			GScr.drawString(CmdBuf.substring(csp), 0, ScrBuf.getHeight() - fm.getMaxDecent() );
			GScr.drawLine(cw, ScrBuf.getHeight() - fh, cw, ScrBuf.getHeight() );
		} else if(StatusRemain != 0) {
			GScr.drawString(StatusBuf, 0, ScrBuf.getHeight() - fm.getMaxDecent() );
		}
	}

	public void changeELEMcoord(int i, int x, int y) {
		ELEMs[i].x = x;
		ELEMs[i].y = y;
	}

	public void changeELEMsize(int i, int w, int h) {
		ELEMs[i].w = w - ELEMs[i].x;
		ELEMs[i].h = h - ELEMs[i].y;
	}

	public void gametick() {
		Point p = MouseInfo.getPointerInfo().getLocation();
		GMouseX = (int)p.getX();
		GMouseY = (int)p.getY();
		if(JRobot != null) {		
			ScrCap = JRobot.createScreenCapture(new Rectangle(ScrCapX, ScrCapY, ScrCapW, ScrCapH) );
		}
		if(isIDValid(MoveMode) ) {
			changeELEMcoord(MoveMode, MouseX, MouseY);
		}
		if(VTMoveMode != null) {
			int IDS[] = {VTEyeID, VTMouthIDA, VTMouthIDB};
			for(int i = 0; i < IDS.length; i++) {
				if(isIDValid(i) ) {
					changeELEMcoord(i, VTMoveMode[i * 2] + MouseX, VTMoveMode[i * 2 + 1] + MouseY);
				}
			}
		}
		/*
		if(isIDValid(ResizeMode) ) {
			ELEMs[ResizeMode].w = MouseX - ELEMs[ResizeMode].x;
			ELEMs[ResizeMode].h = MouseY - ELEMs[ResizeMode].y;
		}
		if(VTResizeMode != null) {
			double wd = (double)ELEMs[VTBodyID].w / (double)ELEMs[VTBodyID].img.getWidth();
			double  hd = (double)ELEMs[VTBodyID].h / (double)ELEMs[VTBodyID].img.getHeight();
			int IDS[] = {VTEyeID, VTMouthIDA, VTMouthIDB};
			for(int i = 0; i < IDS.length; i++) {
				if(isIDValid(i) ) {
					ELEMs[i].w = VTResizeMode[i * 2] + (int)( (double)ELEMs[i].w * wd);
					ELEMs[i].h = VTResizeMode[i * 2 + 1] + (int)( (double)ELEMs[i].h * hd);
				}
			}
		}
		*/
		if(StatusRemain != 0 && StatusRemain != -1) {
			StatusRemain--;
		}
		if(LAudioIn != null) {
			int rl = LAudioIn.available();
			int br = AudioBuffer.length - AudioBufferCur;
			if(rl > br) {
				rl = br;
			}
			int r = LAudioIn.read(AudioBuffer, AudioBufferCur, br);
			if(r == -1) {
				System.out.printf("Audio device read failed.\n");
				LAudioIn.close();
				LAudioIn = null;
			} else {
				AudioBufferCur += r;
			}
			if(AudioBufferCur == AudioBuffer.length) {
				AudioBufferCur = 0;/*
				System.out.println("Data feeded:");
				for(int i = 0; i < 80; i++) {
					int e = Byte.toUnsignedInt(AudioBuffer[i]) - 128;
					System.out.printf("%d; ", e);
				}
				System.out.println();*/
				int m = 0;
				for(byte eb : AudioBuffer) {
					int e = Byte.toUnsignedInt(eb) - 128;
					if(e > m) {
						m = e;
					}
				}
				AudioLevel = m;
			}
		}
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand().equals("DRAW") ) {
			draw();
			PPanel.repaint();
		} else if(e.getActionCommand().equals("TICK") ) {
			gametick();
		}
	}
}

class ImageElements {
	int x;
	int y;
	int w;
	int h;
	boolean v;
	String fn;
	BufferedImage img;
	public ImageElements(String i, int _x, int _y, int _w, int _h) throws IOException {
		fn = i;
		img = ImageIO.read(new File(fn) );
		x = _x;
		y = _y;
		v = true;
		if(_w != 0 && _h != 0) {
			w = _w;
			h = _h;
		} else {
			w = img.getWidth();
			h = img.getHeight();
		}
	}
}
