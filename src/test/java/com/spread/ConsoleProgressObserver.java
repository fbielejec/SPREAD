package com.spread;

import com.spread.progress.IProgressObserver;

public class ConsoleProgressObserver extends Thread implements IProgressObserver {

    private static final String anim = "|/-\\";

    private boolean showProgress;
    private double progress;
    private final int barLength;

    public ConsoleProgressObserver() {
        this.barLength = 100;
        this.showProgress = true;
        this.progress = 0.0;
    }

    @Override
    public void handleProgress(double progress) {
        assert ((progress >= 0.0) && (progress <= 1.0)) : "Got progress value " + progress + ". Make sure that 0 ≤ progress ≤ 1";
        this.progress = progress;
    }

    public void start() {
        System.out.println("0                        25                       50                       75                       100%");
        System.out.println("|------------------------|------------------------|------------------------|------------------------|");
        super.start ();
    }

    public void run() {
        int i = 0;
        while (showProgress) {

            int column = (int) (progress * barLength);
            int j = 0;

            String progressIndicator = "\r[";
            for (; j < column - 1; j++) {
                progressIndicator += ("*");
            }

            String whitespace = "";
            for (; j < barLength - 2; j++) {
                whitespace += (" ");
            }
            whitespace += ("]");

            System.out.print(progressIndicator + anim.charAt(i++ % anim.length()) + whitespace);

            if (progress >= 1) {
                showCompleted();
                this.showProgress = false;
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

        }
    }

    private void showCompleted() {
        String progress = "\r[";
        for (int i = 0; i < barLength - 1; i++) {
            progress += ("*");
        }
        progress += ("]");
        System.out.print(progress);
        // System.out.print("\n");
    }

}
