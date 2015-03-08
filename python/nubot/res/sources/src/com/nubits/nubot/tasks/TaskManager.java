/*
 * Copyright (C) 2014 desrever <desrever at nubits.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.nubits.nubot.tasks;

import com.nubits.nubot.global.Global;
import com.nubits.nubot.notifications.HipChatNotifications;
import com.nubits.nubot.notifications.jhipchat.messages.Message.Color;
import com.nubits.nubot.tasks.strategy.PriceMonitorTriggerTask;
import com.nubits.nubot.tasks.strategy.StrategyPrimaryPegTask;
import com.nubits.nubot.tasks.strategy.StrategySecondaryPegTask;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 *
 * @author desrever < desrever@nubits.com >
 */
public class TaskManager {

    private static final Logger LOG = Logger.getLogger(TaskManager.class.getName());
    private static final String STRATEGY_FIAT = "Strategy Fiat Task";
    private static final String STRATEGY_CRYPTO = "Strategy Crypto Task";
    //Class Variables
    protected int interval;
    private BotTask checkConnectionTask;
    private BotTask strategyFiatTask;
    private BotTask sendLiquidityTask;
    private BotTask checkNudTask;
    private BotTask priceMonitorTask; //use only with NuPriceMonitor
    //these are used for secondary peg strategy
    private BotTask secondaryPegTask;
    private BotTask priceTriggerTask;
    //--------------end secondary peg tasks
    private ArrayList<BotTask> taskList;
    private boolean running;
    private boolean initialized = false;
//Constructor

    public TaskManager() {
        this.running = false;
        taskList = new ArrayList<BotTask>();
        //assign default values just for testing without Global.options loaded
        int sendLiquidityInterval = Integer.parseInt(Global.settings.getProperty("submit_liquidity_seconds")),
                executeStrategyInterval = 41,
                checkPriceInterval = 61;

        boolean verbose = false;

        if (Global.options != null) {
            //If global option have been loaded
            sendLiquidityInterval = Global.options.getSendLiquidityInteval();
            executeStrategyInterval = Global.options.getExecuteStrategyInterval();
            verbose = Global.options.isVerbose();
        }


        checkConnectionTask = new BotTask(
                new CheckConnectionTask(), 127, "checkConnection");
        taskList.add(checkConnectionTask);

        sendLiquidityTask = new BotTask(
                new SubmitLiquidityinfoTask(verbose), sendLiquidityInterval, "sendLiquidity"); //true for verbosity
        taskList.add(sendLiquidityTask);

        checkNudTask = new BotTask(
                new CheckNudTask(), 30, "checkNud");
        taskList.add(checkNudTask);

        strategyFiatTask = new BotTask(
                new StrategyPrimaryPegTask(), executeStrategyInterval, STRATEGY_FIAT);
        taskList.add(strategyFiatTask);

        secondaryPegTask = new BotTask(
                new StrategySecondaryPegTask(), executeStrategyInterval, STRATEGY_CRYPTO);
        taskList.add(secondaryPegTask);

        priceTriggerTask = new BotTask(
                new PriceMonitorTriggerTask(), checkPriceInterval, "priceTriggerTask");
        taskList.add(secondaryPegTask);

        priceMonitorTask = new BotTask(
                new NuPriceMonitorTask(), checkPriceInterval, STRATEGY_CRYPTO);
        taskList.add(priceMonitorTask);

        initialized = true;
    }

    //Methods
    public void startAll() {
        for (int i = 0; i < taskList.size(); i++) {
            BotTask task = taskList.get(i);
            task.start();
        }

    }

    public void stopAll() {
        LOG.fine("\nStopping all tasks : -- ");
        boolean sentNotification = false;
        for (int i = 0; i < taskList.size(); i++) {

            BotTask bt = taskList.get(i);
            if (bt.getName().equals(STRATEGY_FIAT) || bt.getName().equals(STRATEGY_CRYPTO)) {
                if (!sentNotification) {
                    String additionalInfo = "";
                    if (Global.options != null) {
                        additionalInfo = Global.options.getExchangeName() + " " + Global.options.getPair().toString("_");
                    }
                    HipChatNotifications.sendMessage("Bot shut-down ( " + additionalInfo + " )", Color.RED);
                    sentNotification = true;
                }
            }
            LOG.fine("Shutting down " + bt.getName());
            try {
                bt.getTimer().cancel();
                bt.getTimer().purge();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                System.exit(0);
            }

        }
    }

    public void printTasksStatus() {
        for (int i = 0; i < taskList.size(); i++) {
            BotTask task = taskList.get(i);
            LOG.fine("Task name : " + task.getName() + ""
                    + " running : " + task.isRunning());
        }
    }

    /**
     * @return the interval
     */
    public int getInterval() {
        return interval;
    }

    /**
     * @param interval the interval to set
     */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    /**
     * @return the isRunning
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @param isRunning the isRunning to set
     */
    public void setRunning(boolean isRunning) {
        this.running = isRunning;
    }

    public BotTask getCheckConnectionTask() {
        return checkConnectionTask;
    }

    public void setCheckConnectionTask(BotTask checkConnectionTask) {
        this.checkConnectionTask = checkConnectionTask;
    }

    public BotTask getStrategyFiatTask() {
        return strategyFiatTask;
    }

    public void setStrategyFiatTask(BotTask strategyFiatTask) {
        this.strategyFiatTask = strategyFiatTask;
    }

    public BotTask getSendLiquidityTask() {
        return sendLiquidityTask;
    }

    public BotTask getSecondaryPegTask() {
        return secondaryPegTask;
    }

    public void setSecondaryPegTask(BotTask secondaryPegTask) {
        this.secondaryPegTask = secondaryPegTask;
    }

    public BotTask getPriceTriggerTask() {
        return priceTriggerTask;
    }

    public void setPriceTriggerTask(BotTask priceTriggerTask) {
        this.priceTriggerTask = priceTriggerTask;
    }

    public void setSendLiquidityTask(BotTask slt) {
        this.sendLiquidityTask = slt;
    }

    public ArrayList<BotTask> getTaskList() {
        return taskList;
    }

    public void setTaskList(ArrayList<BotTask> taskList) {
        this.taskList = taskList;
    }

    public BotTask getCheckNudTask() {
        return checkNudTask;
    }

    public void setCheckNudTask(BotTask checkNudTask) {
        this.checkNudTask = checkNudTask;
    }

    public BotTask getPriceMonitorTask() {
        return priceMonitorTask;
    }

    public void setPriceMonitorTask(BotTask priceMonitorTask) {
        this.priceMonitorTask = priceMonitorTask;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
