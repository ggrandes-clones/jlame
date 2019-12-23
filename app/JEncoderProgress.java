package app;

final class JEncoderProgress {
	final Jtimestatus real_time = new Jtimestatus();
	final Jtimestatus proc_time = new Jtimestatus();
	double last_time;
	int    last_frame_num;
	boolean time_status_init;
}
