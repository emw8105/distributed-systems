To start the program:
	1. Navigate to the project folder where Node.java is located using "cd {directory}"
	2. Run the command "javac Node.java" to create a java class file
	3. Run the command "java Node dcXX dcXX dcXX dcXX", where dcXX is any UTD lab machine from dc01-dc45
		the first dcXX should be the host that the process is running on, i.e.
		if running on dc30, then one might do "java Node dc30 dc34 dc22 dc09"
	4. Run the same command on 3 other processes, continuing the same pattern
		ex: on dc30, run:	 "java Node dc30 dc34 dc22 dc09"
		then on dc22, run:	 "java Node dc22, dc34, dc30, dc09"
		then on dc34, run:	 "java Node dc34, dc30, dc22, dc09"
		then on dc09, run:	 "java Node dc09, dc30, dc34, dc22"
	5. The processes will wait until all 4 have been initialized by the user, whereupon the program will start broadcasting

Alternatively, if the testHosts files are installed along with the program directory:
	1. Navigate to the project folder where Node.java is located using "cd {directory}"
	2. Run the command "javac Node.java" to create a java class file
	3. Run the command "java Node < testHosts1.txt"
	4. Repeat for each process using the next testHosts file, ex:
		ex: on dc30, run:	 "java Node < testHosts1.txt"
		then on dc22, run:	 "java Node < testHosts2.txt"
		then on dc34, run:	 "java Node < testHosts3.txt"
		then on dc09, run:	 "java Node < testHosts4.txt"