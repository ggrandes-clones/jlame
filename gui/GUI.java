package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import app.Jconsole;
import app.Jlame_main;
import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;

public final class GUI extends JFrame implements ActionListener {
	private static final long serialVersionUID = 1L;
	private static final String APP_NAME = "Lame";
	private static final Font FONT = new Font( "Monospaced", Font.PLAIN, 12 );
	//
	/** Расширение файлов графических ресурсов */
	private static final String IMG_EXT = ".png";
	/**
	 * Загрузить изображение с заданным именем из ресурсов.
	 * @param name имя изображения без расширения
	 * @return BufferedImage, буферизованное изображение
	 *     или <code>null</code> если не удаётся загрузить ресурс
	 */
	public static final BufferedImage loadImage(final String name) {
		try {
			return ImageIO.read( GUI.class.getResource( "/img/" + name + IMG_EXT ) );
		} catch(final IllegalArgumentException e) {
			//throw new IOException( e.getMessage() );
		} catch( final IOException e ) {
		}
		return null;
	}
	//
	final JTextArea mTxtInput = new JTextArea();
	//final JTextPane mTxtConsole = new JTextPane();
	final JConsole mTxtConsole = new JConsole( 100, 200 );
	private final JButton mBtnLoad = new JButton( Localization.get("BtnLoad") );
	private final JButton mBtnSave = new JButton( Localization.get("BtnSave") );
	private final JButton mBrnStart = new JButton( Localization.get("BtnStart") );
	//
	private String mCurrentDirectory = null;
	//
	public GUI() {
		setName( APP_NAME );
		setTitle( APP_NAME );
		setIconImage( loadImage("ico") );
		//
		setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		addWindowListener( new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent e) {
					exit();
				}
			}
		);
		//
		mTxtInput.setBorder( BorderFactory.createTitledBorder( Localization.get("LblInput") ) );
		mTxtInput.setFont( FONT );
		//
		mTxtConsole.setBorder( BorderFactory.createTitledBorder( Localization.get("LblConsole")) );
		mTxtConsole.setFont( FONT );
		mTxtConsole.captureStdOut();
		mTxtConsole.captureErrOut();
		//
		final JSplitPane split = new JSplitPane( JSplitPane.VERTICAL_SPLIT, true, new JScrollPane( mTxtInput ), new JScrollPane( mTxtConsole ) );
		split.setDividerLocation( 0.20 );
		split.setResizeWeight( 0.20 );
		add( split, BorderLayout.CENTER );
		//
		mBtnLoad.addActionListener( this );
		mBtnSave.addActionListener( this );
		mBrnStart.addActionListener( this );
		final JPanel panel = new JPanel();
		panel.add( mBtnLoad );
		panel.add( mBtnSave );
		panel.add( mBrnStart );
		add( panel, BorderLayout.PAGE_END );
		//
		setPreferredSize( new Dimension( 800, 600 ) );
		setMinimumSize( getPreferredSize() );
		pack();// установка предпочтительных размеров
		setLocationRelativeTo( null );// по центру экрана
		setResizable( true );
		setVisible( true );
		//
		mTxtInput.setText("--help");
		mBrnStart.doClick();
	}
	/** Exit. */
	public void exit() {
		//
		setVisible( false );
		dispose();
		//
		System.exit( 0 );
	}
	@Override
	public void actionPerformed(final ActionEvent e) {
		final Object source = e.getSource();
		if( source == mBtnLoad ) {
			final JFileChooser fc = new JFileChooser( mCurrentDirectory );
			fc.setFileSelectionMode( JFileChooser.FILES_ONLY );
			fc.setMultiSelectionEnabled( false );
			final FileNameExtensionFilter filter = new FileNameExtensionFilter( "Lame config (*.txt)", "txt" );
			fc.addChoosableFileFilter( filter );
			fc.setFileFilter( filter );
			fc.setSelectedFile( new File("config.txt") );
			if( fc.showOpenDialog( this ) == JFileChooser.APPROVE_OPTION ) {
				mCurrentDirectory = fc.getSelectedFile().getParent();
				BufferedReader br = null;
				try {
					br = new BufferedReader( new InputStreamReader( new FileInputStream( fc.getSelectedFile() ), "UTF-8" ) );
					mTxtInput.setText("");
					String x;
					while( (x = br.readLine()) != null ) {
						mTxtInput.append( x );
					}
				} catch ( final Exception ex ) {
					System.out.println( ex );
				} finally {
					if( br != null ) {
						try { br.close(); } catch( final IOException e1 ) {}
					}
				}
			}
		} else if( source == mBtnSave ) {
			final JFileChooser fc = new JFileChooser( mCurrentDirectory );
			fc.setFileSelectionMode( JFileChooser.FILES_ONLY );
			fc.setMultiSelectionEnabled( false );
			final FileNameExtensionFilter filter = new FileNameExtensionFilter( "Lame config (*.txt)", "txt" );
			fc.addChoosableFileFilter( filter );
			fc.setFileFilter( filter );
			fc.setSelectedFile( new File("config.txt") );
			if( fc.showSaveDialog( this ) == JFileChooser.APPROVE_OPTION ) {
				mCurrentDirectory = fc.getSelectedFile().getParent();
				PrintStream out = null;
				try {
					out = new PrintStream( fc.getSelectedFile() );
					out.println( mTxtInput.getText() );
				} catch(final IOException ie) {
					System.err.println( ie.getMessage() );
				} finally {
					if( out != null ) {
						out.close();
					}
				}
			}
		} else if( source == mBrnStart ) {
			mBrnStart.setEnabled( false );
			//
			final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

				@Override
				protected Void doInBackground() throws Exception {
					mTxtConsole.clear();
					Jconsole.frontend_open_console();
					final Jlame_global_flags gf = Jlame.lame_init();
					Jlame_main.lame_main( gf, mTxtInput.getText().split("[ \n]") );
					Jlame.lame_close( gf );
					return null;
				}
				@Override
				protected void done() {
					try {// to catch exceptions in the doInBackground()
						get();
					} catch (final Exception ex) {
						ex.printStackTrace();
						// firePropertyChange("done-exception", null, ex);
					}
					mBrnStart.setEnabled( true );
				}
			};
			worker.execute();
		}
	}
}
