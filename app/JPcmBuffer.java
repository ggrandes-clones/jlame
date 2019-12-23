package app;

import java.util.Arrays;

final class JPcmBuffer {
	/** buffer for each channel */
	final Object ch[] = new Object[2];
	/** sample width */
	// int     w;// FIXME w never uses
	/** number samples allocated */
	int     n;
	/** number samples used */
	int     u;
	/** number samples to ignore at the beginning */
	int     skip_start;
	/** number samples to ignore at the end */
	int     skip_end;
	//
	final void initPcmBuffer(final int width) {
		this.ch[0] = null;
		this.ch[1] = null;
		// this.w = width;
		this.n = 0;
		this.u = 0;
		this.skip_start = 0;
		this.skip_end = 0;
	}

	final void freePcmBuffer() {
		this.ch[0] = null;
		this.ch[1] = null;
		this.n = 0;
		this.u = 0;
	}

	final int addPcmBuffer(final short[] a0, final short[] a1, final int read) {
		if( read < 0 ) {
			return this.u - this.skip_end;
		}
		if( this.skip_start >= read ) {
			this.skip_start -= read;
			return this.u - this.skip_end;
		}
		final int a_n = read - this.skip_start;

		if( a_n > 0 ) {
			final int a_skip = /*this.w * */this.skip_start;
			final int a_want = /*this.w * */a_n;
			final int b_used = /*this.w * */this.u;
			final int b_have = /*this.w * */this.n;
			final int  b_need = /*this.w * */(this.u + a_n);
			if( b_have < b_need ) {
				this.n = this.u + a_n;
				this.ch[0] = this.ch[0] == null ? new short[b_need] : Arrays.copyOf( (short[])this.ch[0], b_need );
				this.ch[1] = this.ch[1] == null ? new short[b_need] : Arrays.copyOf( (short[])this.ch[1], b_need );
			}
			this.u += a_n;
			if( this.ch[0] != null && a0 != null ) {
				System.arraycopy( a0, a_skip, this.ch[0], b_used, a_want );
			}
			if( this.ch[1] != null && a1 != null ) {
				System.arraycopy( a1, a_skip, this.ch[1], b_used, a_want );
			}
		}
		this.skip_start = 0;
		return this.u - this.skip_end;
	}
	final int addPcmBuffer(final int[] a0, final int[] a1, final int read) {
		if( read < 0 ) {
			return this.u - this.skip_end;
		}
		if( this.skip_start >= read ) {
			this.skip_start -= read;
			return this.u - this.skip_end;
		}
		final int a_n = read - this.skip_start;

		if( a_n > 0 ) {
			final int a_skip = /*this.w * */this.skip_start;
			final int a_want = /*this.w * */a_n;
			final int b_used = /*this.w * */this.u;
			final int b_have = /*this.w * */this.n;
			final int  b_need = /*this.w * */(this.u + a_n);
			if( b_have < b_need ) {
				this.n = this.u + a_n;
				this.ch[0] = this.ch[0] == null ? new int[b_need] : Arrays.copyOf( (int[])this.ch[0], b_need );
				this.ch[1] = this.ch[1] == null ? new int[b_need] : Arrays.copyOf( (int[])this.ch[1], b_need );
			}
			this.u += a_n;
			if( this.ch[0] != null && a0 != null ) {
				System.arraycopy( a0, a_skip, this.ch[0], b_used, a_want );
			}
			if( this.ch[1] != null && a1 != null ) {
				System.arraycopy( a1, a_skip, this.ch[1], b_used, a_want );
			}
		}
		this.skip_start = 0;
		return this.u - this.skip_end;
	}

	final int takePcmBuffer(final short[] a0, final short[] a1, int a_n, final int mm) {
		if( a_n > mm ) {
			a_n = mm;
		}
		if( /*b != null && */a_n > 0 ) {
			final int a_take = /*this.w * */a_n;
			if( a0 != null && this.ch[0] != null ) {
				System.arraycopy( this.ch[0], 0, a0, 0, a_take );
			}
			if( a1 != null && this.ch[1] != null ) {
				System.arraycopy( this.ch[1], 0, a1, 0, a_take );
			}
			this.u -= a_n;
			if( this.u < 0 ) {
				this.u = 0;
				return a_n;
			}
			if( this.ch[0] != null ) {
				System.arraycopy( this.ch[0], a_take, this.ch[0], 0, /*this.w * */this.u );
			}
			if( this.ch[1] != null ) {
				System.arraycopy( this.ch[1], a_take, this.ch[1], 0, /*this.w * */this.u );
			}
		}
		return a_n;
	}
	final int takePcmBuffer(final int[] a0, final int[] a1, int a_n, final int mm) {
		if( a_n > mm ) {
			a_n = mm;
		}
		if( /*b != null && */a_n > 0 ) {
			final int a_take = /*this.w * */a_n;
			if( a0 != null && this.ch[0] != null ) {
				System.arraycopy( this.ch[0], 0, a0, 0, a_take );
			}
			if( a1 != null && this.ch[1] != null ) {
				System.arraycopy( this.ch[1], 0, a1, 0, a_take );
			}
			this.u -= a_n;
			if( this.u < 0 ) {
				this.u = 0;
				return a_n;
			}
			if( this.ch[0] != null ) {
				System.arraycopy( this.ch[0], a_take, this.ch[0], 0, /*this.w * */this.u );
			}
			if( this.ch[1] != null ) {
				System.arraycopy( this.ch[1], a_take, this.ch[1], 0, /*this.w * */this.u );
			}
		}
		return a_n;
	}
}
