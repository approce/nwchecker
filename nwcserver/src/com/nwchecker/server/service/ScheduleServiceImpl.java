package com.nwchecker.server.service;

import com.nwchecker.server.model.Contest;
import com.nwchecker.server.utils.ContestComparator;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Service(value = "ScheduleService")
public class ScheduleServiceImpl implements ScheduleService {

    @Autowired
    private ContestService contestService;

    @Autowired
    private ScoreCalculationService scoreCalculationService;

    @Autowired
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> nextTaskExecution;

    private static final Logger LOG
            = Logger.getLogger(ScheduleServiceImpl.class);

    @Override
    public void refresh() {
        //if next task registered- cancel it:
        if (nextTaskExecution != null) {
            if (!nextTaskExecution.isDone()) {
                nextTaskExecution.cancel(false);
            }
        }

        //get all contests for which need Timer task:
        //get release contests:
        List<Contest> executableContests = contestService.getContestByStatus(Contest.Status.RELEASE);
        //add going contests:
        executableContests.addAll(contestService.getContestByStatus(Contest.Status.GOING));
        if (executableContests.size() == 0) {
            return;
        }
        //sort contests in execution time order:
        Collections.sort(executableContests, new ContestComparator());

        //for first in list- create task timer:
        final Contest nextExecuteContest = executableContests.get(0);

        long executionTime = 0;
        if (nextExecuteContest.getStatus().equals(Contest.Status.RELEASE)) {
            executionTime = nextExecuteContest.getStarts().getTime();
            LOG.debug("Register start contest action for contest id=" + nextExecuteContest.getId());
        } else {
            executionTime = nextExecuteContest.getStarts().getTime();
            executionTime += nextExecuteContest.getDuration().getTime();
            executionTime += 2 * 60 * 60 * 1000;
            LOG.debug("Register stop contest action for contest id=" + nextExecuteContest.getId());
        }

        nextTaskExecution = taskScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (nextExecuteContest.getStatus() == Contest.Status.RELEASE) {
                    nextExecuteContest.setStatus(Contest.Status.GOING);
                    contestService.updateContest(nextExecuteContest);
                    LOG.debug("Contest (id=" + nextExecuteContest.getId() + ") changed status to GOING");
                } else {
                    nextExecuteContest.setStatus(Contest.Status.ARCHIVE);
                    nextExecuteContest.setHidden(true);
                    contestService.updateContest(nextExecuteContest);
                    scoreCalculationService.calculateScore(nextExecuteContest.getId());
                    LOG.debug("Contest (id=" + nextExecuteContest.getId() + ") changed status to ARCHIVE");
                }
                refresh();
            }
        }, new Date(executionTime));
    }
}


