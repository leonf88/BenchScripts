## Install the frameworks to bench

Download the frameworks with `bash setup.sh`, including

* Hadoop
* Spark
* Flink
* Mahout
* Mpich

### Hadoop

    # format the directories
    bin/hadoop namenode -format
    # start dfs
    sbin/start-dfs.sh

Check the log and web page to make sure the HDFS is start correctly

    $HADOOP_HOME/logs/hadoop-lf-namenode-lingcloud21.log
    http://lingcloud21:9001/dfshealth.html#tab-datanode

### Flink

### Spark

*  Compile Spark with scala 2.11

        export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512M -XX:ReservedCodeCacheSize=512m"
        ./dev/change-scala-version.sh 2.11
        mvn -Pyarn -Phadoop-2.6 -Dscala-2.11 -DskipTests clean package
        ./make-distribution.sh --tgz  -Phadoop-2.6 -Pyarn -Pscala-2.11 -PskipTests

### DataMPI

* Install mvapich (seems unstable)

        ./configure --prefix=$HOME/opt/mvapich2 --with-device=ch3:nemesis \
        	    CFLAGS=-fPIC --disable-f77 --disable-fc
        make -j4 && make install

        rm -rf $HOME/opt/mpi
        ln -s $HOME/opt/mvapich2 $HOME/opt/mpi
        MPI_HOME=$HOME/opt/mpi
        PATH=$MPI_HOME/bin:$PATH
        LD_LIBRARY_PATH=$MPI_HOME/lib:$LD_LIBRARY_PATH
        export LD_LIBRARY_PATH

* Install mpich

        ./configure --prefix=$HOME/opt/mpich3 --with-device=ch3:nemesis \
            --enable-romio --enable-nemesis-dbg-localoddeven --enable-fast=O0 \
            CFLAGS=-fPIC --disable-fortran
        make -j4 && make install

        rm -rf $HOME/opt/mpi
        ln -s $HOME/opt/mpich3 $HOME/opt/mpi
        MPI_HOME=$HOME/opt/mpi
        PATH=$MPI_HOME/bin:$PATH
        LD_LIBRARY_PATH=$MPI_HOME/lib:$LD_LIBRARY_PATH
        export LD_LIBRARY_PATH

* Install DataMPI

        cmake -D CMAKE_INSTALL_PREFIX=$HOME/BenchScripts/frameworks/datampi-batch \
       	    -D MPI_D_BUILD_DOCS=OFF -D MPI_D_BUILD_TESTS=OFF \
       	    -D MPI_D_BUILD_EXAMPLES=OFF -D MPI_D_BUILD_BENCHMARKS=OFF \
       	    $HOME/BenchScripts/frameworks/DataMPI

        make install


### Download Others

Those binaries should be install in `$HOME/opt`

    wget --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u112-b15/jdk-8u112-linux-x64.tar.gz
    wget http://downloads.lightbend.com/scala/2.11.8/scala-2.11.8.tgz
    wget https://archive.apache.org/dist/maven/binaries/apache-maven-3.2.2-bin.tar.gz

### AWS cluster setup

Request increase the limit

    Limit increase request 1
    Service: EC2 Instances
    Region: US West (Oregon)
    Primary Instance Type: t2.large
    Limit name: Instance Limit
    New limit value: 100
    ------------
    Use case description: Run experiments, need more instances to scale the test

Ubuntu prepare

    sudo apt-get install -y build-essential wget dstat

Linux prepare

    sudo yum groupinstall "Development Tools"

Update Network Security


| Type | Protocol | Port Range | Source |
| --- | --- | --- | --- |
| All TCP | TCP | 0 - 65535 | 172.31.0.0/16 |
| SSH | TCP | 22 | 0.0.0.0/0 |
| All UDP | TCP | 0 - 65535 | 172.31.0.0/16 |


Color Bash

    alias less='less --RAW-CONTROL-CHARS'
    export LS_OPTS='--color=auto'
    alias ls='ls ${LS_OPTS}'

Prepare Configuration

    cd bin/conf
    sed -i 's/lf/ubuntu/' config.sh
    sed -i 's/172\.22\.1\.21/<IP>/' *
    sed -i 's/\/home\/lf\/workplace/\/home\/ubuntu/' *

    sed -i 's/172\.22\.1\.21/<IP>/' frameworks/hadoop-2.7.3/etc/hadoop/*

    cd dm-benchmakrs
    mvn clean package
    for h in `cat ../bin/conf/slaves`;do ssh $h " mkdir -p /home/ubuntu/BenchScripts/dm-benchmarks/target"; scp target/dm-benchmarks-1.0-SNAPSHOT-jar-with-dependencies.jar $h:`pwd`/target;done

    for h in `cat /home/ubuntu/BenchScripts/bin/s`;do ssh $h " mkdir -p /home/ubuntu/BenchScripts/dm-benchmarks/target"; scp target/dm-benchmarks-1.0-SNAPSHOT-jar-with-dependencies.jar $h:`pwd`/target;done

STEPs:

    ssh-keygen -f "/home/ubuntu/.ssh/known_hosts" -R localhost

    pushd bin/conf
    sed -i 's/lf/ubuntu/' config.sh
    sed -i 's/172\.22\.1\.21/172\.31\.14\.132/' config.sh
    sed -i 's/\/home\/ubuntu\/workplace/\/home\/ubuntu/' config.sh

    vim slaves
    popd

    pushd frameworks/hadoop-2.7.3
    cp ../../bin/conf/slaves etc/hadoop/
    sed -i 's/172\.31\.33\.174/172\.31\.14\.132/' etc/hadoop/*xml
    for h in `cat etc/hadoop/slaves`;do scp -o "StrictHostKeyChecking no" -r -q etc $h:`pwd`;done
    bin/hadoop namenode -format
    sbin/start-dfs.sh
    popd

    pushd frameworks/datampi-batch
    cp ../../bin/conf/slaves conf/hostfile
    for h in `cat conf/hostfile`;do scp -r -q conf $h:`pwd`;done
    popd
