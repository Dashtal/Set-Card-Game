package set.ex;

import set.Env;

public class Player_Bot extends Player {

    public Player_Bot(Env env, Dealer dealer, Table table, int id) {
        super(env, dealer, table, id);
    }

    @Override 
    public void run() {
        playerThread = Thread.currentThread();
        notifyDealer();

        while (!terminate) {
            if (state == gameState.WAITING) {
                try {
                    synchronized(this) {wait();}
                } catch (InterruptedException start) {}
            } else {
                keyInput = (int)(Math.random() * env.config.tableSize);
                executePress();
            }
        }
    }

    @Override
    public void keyPressed(int slot) {
        throw new UnsupportedOperationException("Unsupported method 'keyPressed' for Bot Player.");
    }
}