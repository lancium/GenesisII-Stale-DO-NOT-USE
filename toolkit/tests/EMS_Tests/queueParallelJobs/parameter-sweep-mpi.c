/*Author: Vanamala Venkataswamy */

#include <stdio.h>
#include <stdlib.h>
#include <limits.h>
#include <unistd.h>
#include "mpi.h"

int main(int argc, char **argv)
{

	int id, numprocs, i;
	MPI_Status status;
	char *msg = "Hello";
	char *rmsg = "";
	int ret2=0;
        char command[1000];

        // snag the parameter from command line if we can.
        char *outnum = "x";
        if (argc > 1)
            outnum = argv[1];

	MPI_Init(&argc, &argv);
	MPI_Comm_rank(MPI_COMM_WORLD, &id);
	MPI_Comm_size(MPI_COMM_WORLD, &numprocs);

	if(id == 0)
	{
		for(i=1; i<numprocs; i++)
			MPI_Send(&msg, 15, MPI_CHAR, i, 999, MPI_COMM_WORLD);
	}
	else
	{	
		MPI_Recv(&rmsg, 15, MPI_CHAR, 0, 999, MPI_COMM_WORLD, &status);
		printf("%s from %d\n", rmsg, id);
		sprintf(command, ". ./hostname.sh >> mpi-hostname-%s.txt", outnum);
		ret2= system(command);
		printf("Return code = %d\n",ret2);
	}

	MPI_Finalize();
	return 0;
}


