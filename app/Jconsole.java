package app;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

// http://stackoverflow.com/questions/342990/create-java-console-inside-a-gui-panel

// console.c

public final class Jconsole {
	private static final int CLASS_ID         = 0x434F4E53;
	private static final int REPORT_BUFF_SIZE = 1024;

	// typedef struct console_io_struct {
	int ClassID;
	// unsigned long ClassProt;
	/** filepointer to stream reporting information */
	PrintStream Console_fp;
	/** filepointer to stream fatal error reporting information */
	PrintStream Error_fp;
	/** filepointer to stream reports (normally a text file or /dev/null) */
	PrintStream Report_fp;

	// HANDLE  Console_Handle;

	int    disp_width;
	int    disp_height;
	String str_up;
	String str_clreoln = "";// char    str_clreoln[10];// FIXME str_clreoln is not initialized!
	char   str_emph[] = new char[10];
	char   str_norm[] = new char[10];
	char   Console_buff[] = new char[2048];
	// int     Console_file_type;
	// } Console_IO_t;

	private static final void my_console_printing(final PrintStream fp, final String format, final Object... ap ) {
		if( fp != null ) {
			fp.printf( format, ap );
		}
	}
/* FIXME never uses functions
	private static final void my_error_printing(final PrintStream fp, final String format, final Object... ap) {
		if( fp != null ) {
			fp.printf( format, ap );
		}
	}

	private static final void my_report_printing(final PrintStream fp, final String format, final Object... ap) {
		if( fp != null ) {
			fp.printf( format, ap );
		}
	}
*/
/*
 * Taken from Termcap_Manual.html:
 *
 * With the Unix version of termcap, you must allocate space for the description yourself and pass
 * the address of the space as the argument buffer. There is no way you can tell how much space is
 * needed, so the convention is to allocate a buffer 2048 characters long and assume that is
 * enough.  (Formerly the convention was to allocate 1024 characters and assume that was enough.
 * But one day, for one kind of terminal, that was not enough.)
 */

/* #ifdef HAVE_TERMCAP

	private static final void get_termcap_string(final char const* id, char* dest, size_t n) {
		char    tc[16];
		char   *tp = tc;
		tp[0] = '\0';
		tp = tgetstr(id, &tp);
		if( tp != NULL && dest != NULL && n > 0 ) {
			strncpy(dest, tp, n);
			dest[n-1] = '\0';
		}
	}

	private static final void get_termcap_number(final char const* id, int* dest, int low, int high) {
		int const val = tgetnum(id);
		if( low <= val && val <= high ) {
			*dest = val;
		}
	}

	private static final void apply_termcap_settings(final Jconsole mfp) {
		// try to catch additional information about special console sequences
		final String term_name = System.getenv("TERM");
		if( null != term_name ) {
			final byte term_buff[] = new byte[4096];
			final int ret = tgetent( term_buff, term_name );
			if( 1 == ret ) {
				get_termcap_number("co", &mfp.disp_width, 40, 512);
				get_termcap_number("li", &mfp.disp_height, 16, 256);
				get_termcap_string("up", mfp.str_up, sizeof(mfp.str_up));
				get_termcap_string("md", mfp.str_emph, sizeof(mfp.str_emph));
				get_termcap_string("me", mfp.str_norm, sizeof(mfp.str_norm));
				get_termcap_string("ce", mfp.str_clreoln, sizeof(mfp.str_clreoln));
			}
		}
	}
#endif // TERMCAP_AVAILABLE */

	private static final int init_console(final Jconsole mfp) {
		/* setup basics of brhist I/O channels */
		mfp.disp_width = 80;
		mfp.disp_height = 25;
		mfp.Console_fp = System.err;
		mfp.Error_fp = System.err;
		mfp.Report_fp = null;

		mfp.str_up = "\033[A";

		mfp.ClassID = CLASS_ID;

		return 0;
	}

	private static final void deinit_console(final Jconsole mfp) {
		if( mfp.Report_fp != null ) {
			mfp.Report_fp.close();
			mfp.Report_fp = null;
		}
		mfp.Console_fp.flush();

		Arrays.fill( mfp.Console_buff, 0, REPORT_BUFF_SIZE, (char)0x55 );
	}

	/** LAME console */
	static final Jconsole Console_IO = new Jconsole();

	public static final int frontend_open_console() {
		return init_console( Console_IO );
	}

	static final void frontend_close_console() {
		deinit_console( Console_IO );
	}
/*
	private static final void frontend_debugf(final String format, final Object... ap) {
		my_report_printing( Console_IO.Report_fp, format, ap );
	}

	private static final void frontend_msgf(final String format, final Object... ap) {
		my_console_printing( Console_IO.Console_fp, format, ap );
	}

	private static final void frontend_errorf(final String format, final Object... ap) {
		my_error_printing( Console_IO.Error_fp, format, ap );
	}

	private static final void frontend_print_null(final String format, final Object... args) {
	}
*/
	static final void console_printf(final String format, final Object... args) {
		my_console_printing( Console_IO.Console_fp, format, args );
	}

	static final void error_printf(final String format, final Object... args) {
		my_console_printing( Console_IO.Error_fp, format, args );
	}
/*
	private static final void report_printf(final String format, final Object... args) {
		my_console_printing( Console_IO.Report_fp, format, args );
	}
*/
	static final void console_flush() {
		Console_IO.Console_fp.flush();
	}
/*
	private static final void error_flush() {
		Console_IO.Error_fp.flush();
	}

	private static final void report_flush() {
		Console_IO.Report_fp.flush();
	}
*/
	static final void console_up(int n_lines) {
		while( n_lines-- > 0 ) {
			Console_IO.Console_fp.print( Console_IO.str_up );
		}
		console_flush();
	}

	static final void set_debug_file(final String fn) {
		if( Console_IO.Report_fp == null ) {
			try {
				Console_IO.Report_fp = new PrintStream( new FileOutputStream( fn, true ) );
				error_printf("writing debug info into: %s\n", fn );
			} catch(final FileNotFoundException fe) {
				error_printf("Error: can't open for debug info: %s\n", fn );
			}
		}
	}
}