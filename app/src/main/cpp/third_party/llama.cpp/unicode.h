#ifndef UNICODE_H
#define UNICODE_H

#include <string>
#include <vector>
#include <stdint.h>

std::vector<uint32_t> unicode_cpts_from_utf8(const std::string & utf8);

#endif // UNICODE_H
