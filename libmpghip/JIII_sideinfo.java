package libmpghip;

final class JIII_sideinfo {
	static final class Jch {
		final Jgr_info gr[] = new Jgr_info[2];
		private Jch() {
			gr[0] = new Jgr_info();
			gr[1] = new Jgr_info();
		}
		private final void clear() {
			gr[0].clear();
			gr[1].clear();
		}
	}
	int main_data_begin;
	int private_bits;
	final Jch ch[] = new Jch[2];
	//
	JIII_sideinfo() {
		ch[0] = new Jch();
		ch[1] = new Jch();
	}
	final void clear() {
		main_data_begin = 0;
		private_bits = 0;
		ch[0].clear();
		ch[1].clear();
	}
}
