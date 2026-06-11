#include "unicode.h"

std::vector<uint32_t> unicode_cpts_from_utf8(const std::string & utf8) {
    std::vector<uint32_t> result;
    for (char c : utf8) {
        result.push_back((uint32_t)c); // Simple mock
    }
    return result;
}
