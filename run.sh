#!/bin/bash

########################################
############# CSCI 2951-O ##############
########################################
E_BADARGS=65
if [ $# -le 0 ]
then
	echo "Usage: `basename $0` <input>"
	exit $E_BADARGS
fi
	
input=$1
args="${@:2}"

# export the ilog license to run the solver
#export ILOG_LICENSE_FILE=/gpfs/main/sys/shared/psfu/local/projects/cplex/ilm/current/linux/access.site.ilm
export ILOG_LICENSE_FILE="./license"

# export the solver libraries into the path
#export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/gpfs/main/sys/shared/psfu/local/projects/cplex/CPLEX_Studio_Academic/12.3/x86_64/cplex/bin/x86-64_sles10_4.1
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:"./x64_win64"

# add the solver jar to the classpath and run
#java -cp /gpfs/main/sys/shared/psfu/local/projects/cplex/CPLEX_Studio_Academic/12.3/x86_64/cplex/lib/cplex.jar:src solver.ip.Main $input $args
java -cp "./cplex.jar;src" solver.ip.Main $input $args