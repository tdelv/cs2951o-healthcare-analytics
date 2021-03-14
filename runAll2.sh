#!/bin/bash

########################################
############# CSCI 2951-O ##############
########################################

table="$1"
tag=${2-no_tag}
timeout=${3-120}
args="${@:4}"

if [ -s $table ]; then
    echo "Continuing with file $table."
else
    touch $table
    for f in input/*.*
    do
        printf "%s," "$f" >> $table
    done
    echo "" >> $table
fi

rm -f tmp
./runAll.sh input/ $timeout tmp $args
grep -o "Result: \S* " tmp > tmp2
grep -o -P -e "--|\d*\.\d*" tmp2 > tmp3
cat tmp3 | sed "s/--/----/g" > tmp4
cat tmp4 | tr '\n' ',' >> $table
echo " # $tag" >> $table
rm tmp tmp2 tmp3 tmp4
