package libmp3lame;

import java.io.PrintStream;

// util.h

/********************************************************************
 * internal variables NOT set by calling program, and should not be *
 * modified by the calling program                                  *
 ********************************************************************/
final class Jlame_internal_flags {
	/*
	 * Some remarks to the Class_ID field:
	 * The Class ID is an Identifier for a pointer to this struct.
	 * It is very unlikely that a pointer to lame_global_flags has the same 32 bits
	 * in it's structure (large and other special properties, for instance prime).
	 *
	 * To test that the structure is right and initialized, use:
	 *     if ( gfc -> Class_ID == LAME_ID ) ...
	 * Other remark:
	 *     If you set a flag to 0 for uninit data and 1 for init data, the right test
	 *     should be "if (flag == 1)" and NOT "if (flag)". Unintended modification
	 *     of this element will be otherwise misinterpreted as an init.
	 */
	static final int LAME_ID = 0xFFF88E3B;
	int class_id;

	boolean lame_init_params_successful;
	boolean lame_encode_frame_init;
	boolean iteration_init_init;
	boolean fill_buffer_resample_init;

	final JSessionConfig cfg = new JSessionConfig();

	/* variables used by lame.c */
	final Jbitstream bs = new Jbitstream();
	final JIII_side_info l3_side = new JIII_side_info();

	final Jscalefac_struct scalefac_band = new Jscalefac_struct();

	final JPsyStateVar sv_psy = new JPsyStateVar(); /* DATA FROM PSYMODEL.C */
	final JPsyResult ov_psy = new JPsyResult();
	final JEncStateVar sv_enc = new JEncStateVar(); /* DATA FROM ENCODER.C */
	final JEncResult ov_enc = new JEncResult();
	final JQntStateVar sv_qnt = new JQntStateVar(); /* DATA FROM QUANTIZE.C */

	final JRpgStateVar sv_rpg = new JRpgStateVar();
	final JRpgResult ov_rpg = new JRpgResult();

	/* optional ID3 tags, used in id3tag.c  */
	final Jid3tag_spec tag_spec = new Jid3tag_spec();
	char nMusicCRC;

	//char _unused;

	final JVBR_seek_info VBR_seek_table = new JVBR_seek_info(); /* used for Xing VBR header */

	JATH ATH;         /* all ATH related stuff */

	JPsyConst cd_psy;

	/* used by the frame analyzer */
	Jplotting_data pinfo;
	Jhip hip;

	/* functions to replace with CPU feature optimized versions in takehiro.c */
	/*int     (*choose_table) (const int *ix, const int *const end, int *const s);
	void    (*fft_fht) (FLOAT *, int);
	void    (*init_xrpow_core) (gr_info * const cod_info, FLOAT xrpow[576], int upper,
	                            FLOAT * sum);
	 */
	PrintStream report_msg;
	PrintStream report_dbg;
	PrintStream report_err;

	boolean is_lame_internal_flags_valid() {
		/* if( gfc == null ) {
			return false;
		} */
		return this.class_id == Jlame_internal_flags.LAME_ID && this.lame_init_params_successful;
	}

	/*empty and close mallocs in gfc */

	final void free_id3tag() {
		this.tag_spec.language = "";
		this.tag_spec.title = null;
		this.tag_spec.artist = null;
		this.tag_spec.album = null;
		this.tag_spec.comment = null;

		this.tag_spec.albumart = null;
		// this.tag_spec.albumart_size = 0;
		this.tag_spec.albumart_mimetype = Jid3tag.MIMETYPE_NONE;

		if( this.tag_spec.v2_head != null ) {
			JFrameDataNode node = this.tag_spec.v2_head;
			do {
				node.dsc = null;
				node.txt = null;
				node = node.nxt;
			} while( node != null );
			this.tag_spec.v2_head = null;
			this.tag_spec.v2_tail = null;
		}
	}

	private final void free_global_data() {
		if( /*gfc != null &&*/ this.cd_psy != null ) {
			this.cd_psy.l.s3 = null;
			this.cd_psy.s.s3 = null;
			this.cd_psy = null;
			this.cd_psy = null;
		}
	}

	/* bit stream structure */
	final void freegfc() {
		for( int i = 0; i <= 2 * JEncStateVar.BPC; i++ ) {
			this.sv_enc.blackfilt[i] = null;
		}
		this.sv_enc.inbuf_old[0] = null;
		this.sv_enc.inbuf_old[1] = null;

		this.bs.buf = null;

		this.VBR_seek_table.bag = null;
		// this.VBR_seek_table.size = 0;

		this.ATH = null;

		this.sv_rpg.rgdata = null;
		this.sv_enc.in_buffer_0 = null;
		this.sv_enc.in_buffer_1 = null;

		free_id3tag();

// #ifdef DECODE_ON_THE_FLY
		if( this.hip != null ) {
			this.hip.hip_decode_exit();
			this.hip = null;
		}
// #endif

		free_global_data();
	}
}
