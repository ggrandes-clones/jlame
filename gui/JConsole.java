package gui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import javax.swing.JComponent;
import javax.swing.Scrollable;

/**
 * Консольный вывод для Swing
 */
final class JConsole extends JComponent implements Scrollable {
	private static final long serialVersionUID = 1L;
	private static final Font DEFAULT_FONT = new Font( "Monospaced", Font.PLAIN, 12 );
	// последовательности
	/** Курсор вверх */
	private static final char[] CTRL_UP = {'\033','[','A'};
	//
	private final class TextOutPrintStream extends PrintStream {
		TextOutPrintStream(final OutputStream out) {
			super( out );
		}
		@Override
		public void print(final String s) {
			write( s );
			repaint();
		}
		@Override
		public void print(final char c) {// для перехвата печати спец.символов
			JConsole.this.write( c );
			repaint();
		}
		@Override
		public void flush() {
			super.flush();
			repaint();
		}
	}
	//
	private int mFontBase = 0;
	/** Текст, [строка][колонка] */
	private char[][] mText = new char[1][1];
	/** X позиция курсора */
	private int mCursorX = 0;
	/** Y позиция курсора */
	private int mCursorY = 0;
	// управляющие последовательности
	private static final int MAX_CONTROL_LENGTH = 6;
	private boolean mIsControlSequence = false;
	private final char[] mControl = new char[MAX_CONTROL_LENGTH];
	private int mControlIndex = 0;
	/**
	 * Конструктор по умолчанию. Создаёт консоль 80х40
	 */
	JConsole() {
		this( 80, 40 );
	}
	/**
	 * Конструктор
	 * @param columns количество символов по горизонтали
	 * @param rows количество символов по вертикали
	 */
	JConsole(final int columns, final int rows) {
		setFont( DEFAULT_FONT );
		setConsoleSize( columns, rows );
	}
	@Override
	public final void setFont(final Font font) {
		super.setFont( font );
		final FontMetrics fm = getFontMetrics( font );
		mFontBase = fm.getMaxAscent();
		final Dimension size = new Dimension( mText[0].length * fm.charWidth('W'), mText.length * fm.getHeight() );
		setMinimumSize( size );
		setPreferredSize( size );
	}
	/**
	 * Установить размер консоли в символах
	 * @param columns количество символов по горизонтали (колонок)
	 * @param rows количество символов по вертикали (строк)
	 */
	final void setConsoleSize(final int columns, final int rows) {
		if( columns > 0 && rows > 0 ) {
			if( rows != mText.length || columns != mText[0].length ) {
				final char[][] text = new char[ rows ][ columns ];
				final int columns_to_copy = Math.min( columns, mText[0].length );
				for( int r = 0, rcount = Math.min( rows, mText.length ); r < rcount; r++ ) {
					System.arraycopy( mText[r], 0, text[r], 0, columns_to_copy );
				}
				mText = text;
				final Font font = getFont();
				final FontMetrics fm = getFontMetrics( font );
				final Dimension size = new Dimension( columns * fm.charWidth('W'), rows * font.getSize() );
				setMinimumSize( size );
				setPreferredSize( size );
			}
		}
	}
	/**
	 * Очистить видимое поле
	 */
	final void clear() {
		for( int r = 0, rcount = mText.length; r < rcount; r++ ) {
			Arrays.fill( mText[r], ' ' );
		}
		mCursorX = 0;
		mCursorY = 0;
		mControlIndex = 0;
	}
	/**
	 * Установить курсор в указанную позицию
	 * @param x позиция по гоизонтали
	 * @param y позиция по вертикали
	 */
	final void setCursor(final int x, final int y) {
		if( x >= 0 && x < mText[0].length && y >= 0 && y < mText.length ) {
			mCursorX = x;
			mCursorY = y;
		}
	}
	/**
	 * Сдвинуть текст внутри консоли на 1 строку вверх
	 */
	private final void oneLineUp() {
		for( int r = 1, rcount = mText.length, ccount = mText[0].length; r < rcount; r++ ) {
			System.arraycopy( mText[r], 0, mText[r - 1], 0, ccount );
		}
		mCursorY = mText.length - 1;
		Arrays.fill( mText[mCursorY], ' ' );
	}
	/**
	 * Сдвинуть курсор на следующую позицию
	 */
	private final void moveCorsor() {
		if( ++mCursorX >= mText[0].length ) {
			mCursorX = 0;
			if( ++mCursorY >= mText.length ) {
				oneLineUp();
			}
		}
	}

	/**
	 * Проверить последовательность
	 * @param sequence набор символов последовательности
	 * @return true - совпадает, false - нет.
	 */
	private final boolean checkSequence(final char[] sequence) {
		final int length = mControlIndex;
		if( sequence.length == length ) {
			final char[] c = mControl;
			for( int i = 0; i < length; i++ ) {
				if( c[i] != sequence[i] ) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	/**
	 * Обработать управляющие последовательности
	 * @return true - выполнено, false - нет.
	 */
	private final boolean processControl() {
		if( checkSequence( CTRL_UP ) ) {
			if( --mCursorY < 0 ) {
				mCursorY = 0;
			}
			return true;
		}
		return false;
	}
	/**
	 * Вывести символ.
	 * @param c символ.
	 */
	private final void write(final char c) {
		if( mIsControlSequence ) {
			mControl[mControlIndex++] = c;
			if( processControl() ) {
				mControlIndex = 0;
				mIsControlSequence = false;
				return;
			}
			if( mControlIndex >= MAX_CONTROL_LENGTH ) {
				// если набрали максимум символов и не распознали ни одну из последовательностей
				// просто печатаем то, что набрали.
				mIsControlSequence = false;
				for( int i = 0; i < MAX_CONTROL_LENGTH; i++ ) {
					write( mControl[i] );
				}
			}
			return;
		}
		switch( c ) {
		case '\b':
			if( --mCursorX < 0 ) {
				mCursorX = mText[0].length;
				if( --mCursorY < 0 ) {
					mCursorY = 0;
					mCursorX = 0;
				}
			}
			break;
		case '\t':// табуляция
			for( int i = 0; i < 4; i++ ) {
				moveCorsor();
			}
			break;
		//case '\v':
		//	break;
		case '\r':
			mCursorX = 0;
			break;
		case '\n':
			mCursorX = 0;
			if( ++mCursorY >= mText.length ) {
				oneLineUp();
			}
			break;
		case '\033':// спец символы
			mIsControlSequence = true;
			mControl[mControlIndex++] = c;
			break;
		default:
			mText[mCursorY][mCursorX] = c;
			moveCorsor();
			break;
		}
	}
	/**
	 * Вывести строку.
	 * @param s строка.
	 */
	final void write(final String s) {
		for( int i = 0, length = s.length(); i < length; i++ ) {
			write( s.charAt( i ) );
		}
	}
	/**
	 * Перенаправить поток System.out в окно консоли
	 */
	final void captureStdOut() {
		System.setOut( new TextOutPrintStream( System.out ) );
	}
	/**
	 * Перенаправить поток System.err в окно консоли
	 */
	final void captureErrOut() {
		System.setErr( new TextOutPrintStream( System.err ) );
	}
	@Override
	protected void paintComponent(final Graphics g) {
		final Insets inset = getInsets();
		final int x = inset.left;
		int y = mFontBase + inset.top;
		final int font_height = getFont().getSize();
		for( int r = 0, rows = mText.length, columns = mText[0].length; r < rows; r++ ) {
			g.drawChars( mText[r], 0, columns, x, y );
			y += font_height;
		}
	}

	// Scrollable
	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}
	@Override
	public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
		return getFont().getSize();
	}
	@Override
	public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
		return getFont().getSize() * 10;
	}
	@Override
	public boolean getScrollableTracksViewportWidth() {
		return false;
	}
	@Override
	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
