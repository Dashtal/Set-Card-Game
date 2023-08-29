package set.ex;

import set.Env;

public class Timer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    protected final Dealer dealer;

    /**
     * Notifications being passed between dealer and thread through interrupting threads.
     */
    protected Thread timerThread;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * True iff game should be terminated.
     */
    // Volatile - Timer thread and Dealer thread.
    protected volatile boolean terminate;

    public Timer (Dealer dealer, Env env) {
        this.dealer = dealer;
        this.env = env;
    }

    @Override
    public void run() {
        timerThread = Thread.currentThread();

        while (!terminate) {
            // Wait for dealer to start the timer.
            try {
                synchronized(this) {wait();}
            } catch (InterruptedException start) {}

            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(env.config.turnTimeoutMillis);
            timerLoop();

            dealer.roundFinished = true;
            dealer.dealerThread.interrupt();
        }
    }

    /**
     * The inner loop of the timer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            updateTimerDisplay(timeLeft);
            if (timeLeft > env.config.turnTimeoutWarningMillis & timeLeft > 1000) {
                sleep(1000); // 1 second
            }
            else {
                sleep(1); // 1 millisecond
            }
        }
    }

    /**
     * Sleep for a fixed amount of time.
     */
    private void sleep(long sleepTime) {
        long finishTime = System.currentTimeMillis() + sleepTime;
        while (System.currentTimeMillis() < finishTime & !terminate) {
            try {
                Thread.sleep(Long.max(0, finishTime - System.currentTimeMillis()));
            } catch (InterruptedException ignored) {}
        }
    }
    
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(long timeLeft) {
        if (timeLeft > env.config.turnTimeoutWarningMillis) 
            env.ui.setCountdown(timeLeft + 900, false);
            // + 900 for playability: displays integer part of timeLeft
        else env.ui.setCountdown(timeLeft, true);
    }
}