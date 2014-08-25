#pragma once

#include "common.h"

class Pos
{
public:
	Pos(void);
	~Pos(void);

	static int fromString(const char* s);
	static int fromColAndRow(const int col, const int row);
	static int row(const int val);
	static int col(const int val);
	static void toString(const int val, char* buf);
	static void rowToString(const int val, char* buf);
	static void colToString(const int val, char* buf);
};
