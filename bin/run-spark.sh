#!/usr/bin/env bash

source conf/config.sh

JOB_LIST=(
    #"30M_PR_SPK_1I"
#    "1M_PR_SPK"
#    "10M_PR_SPK"
    "30M_PR_SPK"
#    "web-Google_SPK"
#    "LiveJournal1_SPK"
    "com-friendster_SPK"
)

rm _job_list
for job in ${JOB_LIST[@]}; do
  echo $job >> _job_list
done

#bash $SPARK_HOME/sbin/start-all.sh
bash ./runtest-test.sh
#bash $SPARK_HOME/sbin/stop-all.sh

if [ -d "results-spk" ]; then
   mv results/* results-spk
   rm -r results
else
   mv results/ results-spk
fi

exit