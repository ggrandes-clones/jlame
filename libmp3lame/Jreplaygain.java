package libmp3lame;

/**
 * java replacing:
 * <pre>
 * linpre -> rgData.linprebuf + MAX_ORDER;
 * rinpre -> rgData.rinprebuf + MAX_ORDER;
 * lstep -> rgData.lstepbuf + MAX_ORDER;
 * rstep -> rgData.rstepbuf + MAX_ORDER;
 * lout -> rgData.loutbuf + MAX_ORDER;
 * rout -> rgData.routbuf + MAX_ORDER;
 * </pre>
 */
final class Jreplaygain {
	/** 298640883795 calibration value for 89dB */
	private static final double PINK_REF = 64.82;

	private static final int YULE_ORDER = 10;
	private static final int BUTTER_ORDER = 2;
	// private static final int YULE_FILTER     = filterYule;
	// private static final int BUTTER_FILTER   = filterButter;
	/** percentile which is louder than the proposed level */
	private static final double RMS_PERCENTILE  = 0.95;
	/** maximum allowed sample frequency [Hz] */
	private static final long MAX_SAMP_FREQ   = 48000L;
	private static final long RMS_WINDOW_TIME_NUMERATOR   = 1L;
	/** numerator / denominator = time slice size [s] */
	private static final long RMS_WINDOW_TIME_DENOMINATOR = 20L;
	/** Table entries per dB */
	static final int STEPS_per_dB    = 100;
	/** Table entries for 0...MAX_dB (normal max. values are 70...80 dB) */
	static final int MAX_dB          = 120;

	static final int GAIN_NOT_ENOUGH_SAMPLES = -24601;
	static final int GAIN_ANALYSIS_ERROR = 0;
	private static final int GAIN_ANALYSIS_OK = 1;
	static final int INIT_GAIN_ANALYSIS_ERROR = 0;
	private static final int INIT_GAIN_ANALYSIS_OK = 1;

	@SuppressWarnings("unused")
	static final int MAX_ORDER = (BUTTER_ORDER > YULE_ORDER ? BUTTER_ORDER : YULE_ORDER);
	/** max. Samples per Time slice */
	static final int MAX_SAMPLES_PER_WINDOW = (int)((MAX_SAMP_FREQ * RMS_WINDOW_TIME_NUMERATOR) / RMS_WINDOW_TIME_DENOMINATOR + 1);

	/* for each filter: */
	/* [0] 48 kHz, [1] 44.1 kHz, [2] 32 kHz, [3] 24 kHz, [4] 22050 Hz, [5] 16 kHz, [6] 12 kHz, [7] is 11025 Hz, [8] 8 kHz */

	/*lint -save -e736 loss of precision */

	private static final float ABYule[][] = {//[9][multiple_of(4, 2 * YULE_ORDER + 1)] = {
	    /* 20                 18                 16                 14                 12                 10                 8                  6                  4                  2                 0                 19                 17                 15                 13                 11                 9                  7                  5                  3                  1              */
	    { 0.00288463683916f,  0.00012025322027f,  0.00306428023191f,  0.00594298065125f, -0.02074045215285f,  0.02161526843274f, -0.01655260341619f, -0.00009291677959f, -0.00123395316851f, -0.02160367184185f, 0.03857599435200f, 0.13919314567432f, -0.86984376593551f,  2.75465861874613f, -5.87257861775999f,  9.48293806319790f,-12.28759895145294f, 13.05504219327545f,-11.34170355132042f,  7.81501653005538f, -3.84664617118067f},
	    {-0.00187763777362f,  0.00674613682247f, -0.00240879051584f,  0.01624864962975f, -0.02596338512915f,  0.02245293253339f, -0.00834990904936f, -0.00851165645469f, -0.00848709379851f, -0.02911007808948f, 0.05418656406430f, 0.13149317958808f, -0.75104302451432f,  2.19611684890774f, -4.39470996079559f,  6.85401540936998f, -8.81498681370155f,  9.47693607801280f, -8.54751527471874f,  6.36317777566148f, -3.47845948550071f},
	    {-0.00881362733839f,  0.00651420667831f, -0.01390589421898f,  0.03174092540049f,  0.00222312597743f,  0.04781476674921f, -0.05588393329856f,  0.02163541888798f, -0.06247880153653f, -0.09331049056315f, 0.15457299681924f, 0.02347897407020f, -0.05032077717131f,  0.16378164858596f, -0.45953458054983f,  1.00595954808547f, -1.67148153367602f,  2.23697657451713f, -2.64577170229825f,  2.84868151156327f, -2.37898834973084f},
	    {-0.02950134983287f,  0.00205861885564f, -0.00000828086748f,  0.06276101321749f, -0.00584456039913f, -0.02364141202522f, -0.00915702933434f,  0.03282930172664f, -0.08587323730772f, -0.22613988682123f, 0.30296907319327f, 0.00302439095741f,  0.02005851806501f,  0.04500235387352f, -0.22138138954925f,  0.39120800788284f, -0.22638893773906f, -0.16276719120440f, -0.25656257754070f,  1.07977492259970f, -1.61273165137247f},
	    {-0.01760176568150f, -0.01635381384540f,  0.00832043980773f,  0.05724228140351f, -0.00589500224440f, -0.00469977914380f, -0.07834489609479f,  0.11921148675203f, -0.11828570177555f, -0.25572241425570f, 0.33642304856132f, 0.02977207319925f, -0.04237348025746f,  0.08333755284107f, -0.04067510197014f, -0.12453458140019f,  0.47854794562326f, -0.80774944671438f,  0.12205022308084f,  0.87350271418188f, -1.49858979367799f},
	    { 0.00541907748707f, -0.03193428438915f, -0.01863887810927f,  0.10478503600251f,  0.04097565135648f, -0.12398163381748f,  0.04078262797139f, -0.01419140100551f, -0.22784394429749f, -0.14351757464547f, 0.44915256608450f, 0.03222754072173f,  0.05784820375801f,  0.06747620744683f,  0.00613424350682f,  0.22199650564824f, -0.42029820170918f,  0.00213767857124f, -0.37256372942400f,  0.29661783706366f, -0.62820619233671f},
	    {-0.00588215443421f, -0.03788984554840f,  0.08647503780351f,  0.00647310677246f, -0.27562961986224f,  0.30931782841830f, -0.18901604199609f,  0.16744243493672f,  0.16242137742230f, -0.75464456939302f, 0.56619470757641f, 0.01807364323573f,  0.01639907836189f, -0.04784254229033f,  0.06739368333110f, -0.33032403314006f,  0.45054734505008f,  0.00819999645858f, -0.26806001042947f,  0.29156311971249f, -1.04800335126349f},
	    {-0.00749618797172f, -0.03721611395801f,  0.06920467763959f,  0.01628462406333f, -0.25344790059353f,  0.15558449135573f,  0.02377945217615f,  0.17520704835522f, -0.14289799034253f, -0.53174909058578f, 0.58100494960553f, 0.01818801111503f,  0.02442357316099f, -0.02505961724053f, -0.05246019024463f, -0.23313271880868f,  0.38952639978999f,  0.14728154134330f, -0.20256413484477f, -0.31863563325245f, -0.51035327095184f},
	    {-0.02217936801134f,  0.04788665548180f, -0.04060034127000f, -0.11202315195388f, -0.02459864859345f,  0.14590772289388f, -0.10214864179676f,  0.04267842219415f, -0.00275953611929f, -0.42163034350696f, 0.53648789255105f, 0.04704409688120f,  0.05477720428674f, -0.18823009262115f, -0.17556493366449f,  0.15113130533216f,  0.26408300200955f, -0.04678328784242f, -0.03424681017675f, -0.43193942311114f, -0.25049871956020f}
	};

	private static final float ABButter[][] = {// [9][multiple_of(4, 2 * BUTTER_ORDER + 1)] = {
	    /* 5                4                  3                  2                 1              */
	    {0.98621192462708f, 0.97261396931306f, -1.97242384925416f, -1.97223372919527f, 0.98621192462708f},
	    {0.98500175787242f, 0.97022847566350f, -1.97000351574484f, -1.96977855582618f, 0.98500175787242f},
	    {0.97938932735214f, 0.95920349965459f, -1.95877865470428f, -1.95835380975398f, 0.97938932735214f},
	    {0.97531843204928f, 0.95124613669835f, -1.95063686409857f, -1.95002759149878f, 0.97531843204928f},
	    {0.97316523498161f, 0.94705070426118f, -1.94633046996323f, -1.94561023566527f, 0.97316523498161f},
	    {0.96454515552826f, 0.93034775234268f, -1.92909031105652f, -1.92783286977036f, 0.96454515552826f},
	    {0.96009142950541f, 0.92177618768381f, -1.92018285901082f, -1.91858953033784f, 0.96009142950541f},
	    {0.95856916599601f, 0.91885558323625f, -1.91713833199203f, -1.91542108074780f, 0.95856916599601f},
	    {0.94597685600279f, 0.89487434461664f, -1.89195371200558f, -1.88903307939452f, 0.94597685600279f}
	};

	/** When calling this procedure, make sure that ip[-order] and op[-order] point to real data! */
	private static final void filterYule(final float[] input, int inoffset, final float[] output, int outoffset, int nSamples, final float[] kernel)
	{
		while( nSamples-- != 0 ) {
			final float y0 =  input[inoffset-10] * kernel[ 0];
			final float y2 =  input[inoffset -9] * kernel[ 1];
			final float y4 =  input[inoffset -8] * kernel[ 2];
			final float y6 =  input[inoffset -7] * kernel[ 3];
			final float s00 = y0 + y2 + y4 + y6;
			final float y8 =  input[inoffset -6] * kernel[ 4];
			final float yA =  input[inoffset -5] * kernel[ 5];
			final float yC =  input[inoffset -4] * kernel[ 6];
			final float yE =  input[inoffset -3] * kernel[ 7];
			final float s01 = y8 + yA + yC + yE;
			final float yG =  input[inoffset -2] * kernel[ 8] + input[inoffset -1] * kernel[ 9];
			final float yK =  input[inoffset   ] * kernel[10];

			final float s1 = s00 + s01 + yG + yK;

			final float x1 = output[outoffset-10] * kernel[11] + output[outoffset -9] * kernel[12];
			final float x5 = output[outoffset -8] * kernel[13] + output[outoffset -7] * kernel[14];
			final float x9 = output[outoffset -6] * kernel[15] + output[outoffset -5] * kernel[16];
			final float xD = output[outoffset -4] * kernel[17] + output[outoffset -3] * kernel[18];
			final float xH = output[outoffset -2] * kernel[19] + output[outoffset -1] * kernel[20];

			final float s2 = x1 + x5 + x9 + xD + xH;

			output[outoffset] = (float)(s1 - s2);

			++outoffset;
			++inoffset;
		}
	}

	private static final void filterButter(final float[] input, int inoffset, final float[] output, int outoffset, int nSamples, final float[] kernel) {
		while( nSamples-- != 0 ) {
			final float s1 =  input[inoffset-2] * kernel[0] +  input[inoffset-1] * kernel[2] +  input[inoffset] * kernel[4];
			final float s2 = output[outoffset-2] * kernel[1] + output[outoffset-1] * kernel[3];
			output[outoffset] = (s1 - s2);

			++outoffset;
			++inoffset;
		}
	}
	//
	float linprebuf[] = new float[MAX_ORDER * 2];
	// float[] linpre;     /* left input samples, with pre-buffer */
	float lstepbuf[] = new float[MAX_SAMPLES_PER_WINDOW + MAX_ORDER];
	// float[] lstep;      /* left "first step" (i.e. post first filter) samples */
	float loutbuf[] = new float[MAX_SAMPLES_PER_WINDOW + MAX_ORDER];
	// float[] lout;       /* left "out" (i.e. post second filter) samples */
	float rinprebuf[] = new float[MAX_ORDER * 2];
	// float[] rinpre;     /* right input samples ... */
	float rstepbuf[] = new float[MAX_SAMPLES_PER_WINDOW + MAX_ORDER];
	// float[] rstep;
	float routbuf[] = new float[MAX_SAMPLES_PER_WINDOW + MAX_ORDER];
	// float[] rout;
	int    sampleWindow; /* number of samples required to reach number of milliseconds required for RMS window */
	int    totsamp;
	double lsum;
	double rsum;
	int    freqindex;
	int    first;
	final int A[] = new int[STEPS_per_dB * MAX_dB];
	final int B[] = new int[STEPS_per_dB * MAX_dB];
	//
	/** returns a INIT_GAIN_ANALYSIS_OK if successful, INIT_GAIN_ANALYSIS_ERROR if not */
	private final int ResetSampleFrequency(final int samplefreq) {

		/* zero out initial values */
		for( int i = 0; i < MAX_ORDER; i++ ) {
			this.linprebuf[i] =
			this.lstepbuf[i] =
			this.loutbuf[i] =
			this.rinprebuf[i] =
			this.rstepbuf[i] =
			this.routbuf[i] = 0.f;
		}

		switch( samplefreq ) {
		case 48000:
			this.freqindex = 0;
			break;
		case 44100:
			this.freqindex = 1;
			break;
		case 32000:
			this.freqindex = 2;
			break;
		case 24000:
			this.freqindex = 3;
			break;
		case 22050:
			this.freqindex = 4;
			break;
		case 16000:
			this.freqindex = 5;
			break;
		case 12000:
			this.freqindex = 6;
			break;
		case 11025:
			this.freqindex = 7;
			break;
		case 8000:
			this.freqindex = 8;
			break;
		default:
			return INIT_GAIN_ANALYSIS_ERROR;
		}

		this.sampleWindow = (int)
				((samplefreq * RMS_WINDOW_TIME_NUMERATOR + RMS_WINDOW_TIME_DENOMINATOR - 1) / RMS_WINDOW_TIME_DENOMINATOR);

		this.lsum = 0.;
		this.rsum = 0.;
		this.totsamp = 0;

		final int[] buf = this.A;
		for( int i = 0, ie = buf.length; i < ie; i++ ) {
			buf[i] = 0;
		}

		return INIT_GAIN_ANALYSIS_OK;
	}

	final int InitGainAnalysis(final int samplefreq) {
		if( ResetSampleFrequency( samplefreq ) != INIT_GAIN_ANALYSIS_OK ) {
			return INIT_GAIN_ANALYSIS_ERROR;
		}

		/*
		this.linpre = this.linprebuf + MAX_ORDER;
		this.rinpre = this.rinprebuf + MAX_ORDER;
		this.lstep = this.lstepbuf + MAX_ORDER;
		this.rstep = this.rstepbuf + MAX_ORDER;
		this.lout = this.loutbuf + MAX_ORDER;
		this.rout = this.routbuf + MAX_ORDER;
		*/

		final int[] buf = this.B;
		for( int i = 0, ie = buf.length; i < ie; i++ ) {
			buf[i] = 0;
		}

		return INIT_GAIN_ANALYSIS_OK;
	}

	/** returns GAIN_ANALYSIS_OK if successful, GAIN_ANALYSIS_ERROR if not */
	final int AnalyzeSamples(final float[] left_samples, float[] right_samples, final int offset, final int num_samples, final int num_channels) {

		if( num_samples == 0 ) {
			return GAIN_ANALYSIS_OK;
		}

		int cursamplepos = 0;
		int batchsamples = num_samples;

		switch( num_channels ) {
		case 1:
			right_samples = left_samples;
			break;
		case 2:
			break;
		default:
			return GAIN_ANALYSIS_ERROR;
		}

		if( num_samples < MAX_ORDER ) {
			System.arraycopy( left_samples, offset, this.linprebuf, MAX_ORDER, num_samples );
			System.arraycopy( right_samples, offset, this.rinprebuf, MAX_ORDER, num_samples );
		} else {
			System.arraycopy( left_samples, offset, this.linprebuf, MAX_ORDER, MAX_ORDER );
			System.arraycopy( right_samples, offset, this.rinprebuf, MAX_ORDER, MAX_ORDER );
		}

		while( batchsamples > 0 ) {
			int cursamples = batchsamples > this.sampleWindow - this.totsamp ?
					this.sampleWindow - this.totsamp : batchsamples;
			int curr;
			float[] curleftbuf;// java
			float[] currightbuf;// java
			if( cursamplepos < MAX_ORDER ) {
				curleftbuf = this.linprebuf;
				curr = MAX_ORDER + cursamplepos;// this.linpre + cursamplepos; this.rinpre + cursamplepos;
				currightbuf = this.rinprebuf;
				if( cursamples > MAX_ORDER - cursamplepos ) {
					cursamples = MAX_ORDER - cursamplepos;
				}
			} else {
				curleftbuf = left_samples;
				curr = offset + cursamplepos;// left_samples + cursamplepos; right_samples + cursamplepos;
				currightbuf = right_samples;
			}

			filterYule( curleftbuf, curr,
					this.lstepbuf, MAX_ORDER + this.totsamp,// this.lstep + this.totsamp,
					cursamples,
					ABYule[this.freqindex] );
			filterYule( currightbuf, curr,
					this.rstepbuf, MAX_ORDER + this.totsamp,// this.rstep + this.totsamp,
					cursamples,
					ABYule[this.freqindex] );

			filterButter(
					this.lstepbuf, MAX_ORDER + this.totsamp,// this.lstep + this.totsamp,
					this.loutbuf, MAX_ORDER + this.totsamp,// this.lout + this.totsamp,
					cursamples,
					ABButter[this.freqindex] );
			filterButter(
					this.rstepbuf, MAX_ORDER + this.totsamp,// this.rstep + this.totsamp,
					this.routbuf, MAX_ORDER + this.totsamp,// this.rout + this.totsamp,
					cursamples,
					ABButter[this.freqindex] );

			curleftbuf = this.loutbuf;
			curr = MAX_ORDER + this.totsamp;// this.lout + this.totsamp; /* Get the squared values */ this.rout + this.totsamp;
			currightbuf = this.routbuf;

			float sum_l = 0;
			float sum_r = 0;
			int i = cursamples & 0x03;
			while( i-- != 0 ) {
				final float l = curleftbuf[curr];
				final float r = currightbuf[curr++];
				sum_l += l * l;
				sum_r += r * r;
			}
			i = cursamples >> 2;
			while( i-- != 0 ) {
				final float l0 = curleftbuf[curr + 0];
				final float l1 = curleftbuf[curr + 1];
				final float l2 = curleftbuf[curr + 2];
				final float l3 = curleftbuf[curr + 3];
				final float sl = l0 * l0 + l1 * l1 + l2 * l2 + l3 * l3;
				final float r0 = currightbuf[curr + 0];
				final float r1 = currightbuf[curr + 1];
				final float r2 = currightbuf[curr + 2];
				final float r3 = currightbuf[curr + 3];
				final float sr = r0 * r0 + r1 * r1 + r2 * r2 + r3 * r3;
				sum_l += sl;
				sum_r += sr;
				curr += 4;
			}
			this.lsum += sum_l;
			this.rsum += sum_r;

			batchsamples -= cursamples;
			cursamplepos += cursamples;
			this.totsamp += cursamples;
			if( this.totsamp == this.sampleWindow ) { /* Get the Root Mean Square (RMS) for this set of samples */
				final double val =
						STEPS_per_dB * 10. * Math.log10((this.lsum + this.rsum) / this.totsamp * 0.5 + 1.e-37);
				int ival = (val <= 0.) ? 0 : (int)val;
				if( ival >= this.A.length ) {
					ival = this.A.length - 1;
				}
				this.A[ival]++;
				this.lsum = this.rsum = 0.;
				System.arraycopy( this.loutbuf, this.totsamp, this.loutbuf, 0, MAX_ORDER );
				System.arraycopy( this.routbuf, this.totsamp, this.routbuf, 0, MAX_ORDER );
				System.arraycopy( this.lstepbuf, this.totsamp, this.lstepbuf, 0, MAX_ORDER );
				System.arraycopy( this.rstepbuf, this.totsamp, this.rstepbuf, 0, MAX_ORDER );
				this.totsamp = 0;
			}
			if( this.totsamp > this.sampleWindow) {
				return GAIN_ANALYSIS_ERROR;
			}
		}
		if( num_samples < MAX_ORDER ) {
			System.arraycopy( this.linprebuf, num_samples, this.linprebuf, 0, MAX_ORDER - num_samples );
			System.arraycopy( this.rinprebuf, num_samples, this.rinprebuf, 0, MAX_ORDER - num_samples );
			System.arraycopy( left_samples, offset, this.linprebuf, MAX_ORDER - num_samples, num_samples );
			System.arraycopy( right_samples, offset, this.rinprebuf, MAX_ORDER - num_samples, num_samples );
		} else {
			System.arraycopy( left_samples, offset + num_samples - MAX_ORDER, this.linprebuf, 0, MAX_ORDER );
			System.arraycopy( right_samples, offset + num_samples - MAX_ORDER, this.rinprebuf, 0, MAX_ORDER );
		}

		return GAIN_ANALYSIS_OK;
	}

	private static final float analyzeResult(final int[] Array, final int len) {
		long elems = 0;
		for( int i = 0; i < len; i++) {
			elems += Array[i];
		}
		if( elems == 0 ) {
			return GAIN_NOT_ENOUGH_SAMPLES;
		}

		final long upper = (long) Math.ceil( elems * (1. - RMS_PERCENTILE) );
		long sum = 0;
		int i = len;
		while( i-- > 0 ) {
			sum += Array[i];
			if( sum >= upper ) {
				break;
			}
		}

		return (float) ((float) PINK_REF - (float) i / (float) STEPS_per_dB);
	}

	final float GetTitleGain() {

		final float retval = analyzeResult( this.A, this.A.length );

		for( int i = 0, ie = this.A.length; i < ie; i++ ) {
			this.B[i] += this.A[i];
			this.A[i] = 0;
		}

		for( int i = 0; i < MAX_ORDER; i++) {
			this.linprebuf[i] = this.lstepbuf[i]
				= this.loutbuf[i]
				= this.rinprebuf[i]
				= this.rstepbuf[i]
				= this.routbuf[i] = 0.f;
		}

		this.totsamp = 0;
		this.lsum = this.rsum = 0.f;
		return retval;
	}
}
