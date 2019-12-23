package libmp3lame;

final class JIII_psy_ratio {
	final JIII_psy_xmin thm = new JIII_psy_xmin();
	final JIII_psy_xmin en = new JIII_psy_xmin();
	//
	final void copyFrom(final JIII_psy_ratio r) {
		this.thm.copyFrom( r.thm );
		this.en.copyFrom( r.en );
	}
}
