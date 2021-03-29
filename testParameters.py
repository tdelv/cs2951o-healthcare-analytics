import itertools as it
import os


parameters = [
        ("skip", [str(i) for i in range(1, 10)])
    ]

table = "../table.csv"
timeout = 150

def main():
    os.system("./compile.sh")
    arg_lists = list(map(lambda param: list(map(lambda opt: (param[0], opt), param[1])), parameters))
    for combo in it.product(*arg_lists):
        args = " ".join(list(map(lambda arg: f"-{arg[0]} {arg[1]}", combo)))
        tag = "\"," + ",".join(list(map(lambda arg: ":".join(arg), combo))) + "\""
        os.system(f"./runAll2.sh {table} {tag} {timeout} {args}")

if __name__ == "__main__":
    main()