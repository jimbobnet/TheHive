#!/usr/bin/python

import sys
import os
import json
import pefile
import hashlib
import pydeep
import magic
import pyexifinfo
import re
import pehashng




class file:

    def __init__(self, filepath):

        self.path = filepath
        self.stream = open(filepath, 'r').read()
        try:
            self.pe = pefile.PE(filepath)
            self.pedict = self.pe.dump_dict()
        except Exception as excp:
            print('Failed processing %s') % filepath


    # Magic
    def magic(self):
        return magic.Magic().from_file(self.path)

    # ExifTool
    def exif(self):
        exifreport=pyexifinfo.get_json(self.path)
        result=dict((key,value) for key,value in exifreport[0].iteritems() if not (key.startswith("File") or key.startswith("SourceFile")))
        return result

    # File hash
    def md5(self):
        return hashlib.md5(self.stream).hexdigest();

    def sha1(self):
        return hashlib.sha1(self.stream).hexdigest();

    def sha256(self):
        return hashlib.sha256(self.stream).hexdigest();

    def ssdeep(self):
        return pydeep.hash_file(self.path)

    def imphash(self):
        return self.pe.get_imphash()

    def pehash(self):
        return pehashng.pehashng(self.pe)

    # Fileinfo

    def filesize(self):
        return os.path.getsize(self.path)
    def info(self):
        table=[]
        try:
            for fileinfo in self.pe.FileInfo:
                if fileinfo.Key == 'StringFileInfo':
                    for stringtable in fileinfo.StringTable:
                        for entry in stringtable.entries.items():
                            table.append({'Info':entry[0], 'Value':entry[1]})
            return table
        except Exception as excp:
            return 'None'

    # type
    def PEtype(self):
        if self.pe.is_dll():
            return "DLL"
        if self.pe.is_driver():
            return "DRIVER"
        if self.pe.is_exe():
            return "EXE"

    # Timestamp
    def CompilationTimestamp(self):
        return self.pedict['FILE_HEADER']['TimeDateStamp']['Value']

    # OS Version
    def OperatingSystem(self):
        return str(self.pedict['OPTIONAL_HEADER']['MajorOperatingSystemVersion']['Value']) + "." \
               + str(self.pedict['OPTIONAL_HEADER']['MinorOperatingSystemVersion']['Value'])

    # Machine type
    def Machine(self):
        machinetype = self.pedict['FILE_HEADER']['Machine']['Value']
        mt = {'0x014c': 'x86', '0x0200': 'Itanium', '0x8664': 'x64'}
        return mt[str(hex(machinetype))] if type(machinetype) is int else str(machinetype) + ' => Not x86/64 or Itanium'

    # Entry Point
    def EntryPoint(self):
        return hex(self.pedict['OPTIONAL_HEADER']['AddressOfEntryPoint']['Value'])

    # IAT list of {'entryname':'name', 'symbols':[list of symbols]}
    def iat(self):
        table = []
        for entry in self.pe.DIRECTORY_ENTRY_IMPORT:
            imp = {'entryname': '', 'symbols': []}
            imp['entryname']=entry.dll
            for symbol in entry.imports:
                imp['symbols'].append(symbol.name)
            table.append(imp)
        return table

    # Resources : WORK IN PROGRESS
    def resources(self):
        print self.pe.DIRECTORY_ENTRY_RESOURCE
        for rsrc in self.pe.DIRECTORY_ENTRY_RESOURCE.entries:
            for entry in rsrc.directory.entries:
                print entry.name.__str__()
                for i in entry.directory.entries:
                    print i.data.lang
                    print i.data.sublang

    # Sections list of {Name, Size, Entropy, MD5, SHA1, SHA256, SHA512}
    def sections(self):
        table = []
        for entry in self.pe.sections:
            sect = {'entryname':str(entry.Name),'SizeOfRawData':hex(entry.SizeOfRawData),
                    'Entropy':entry.get_entropy(),
                    'MD5':entry.get_hash_md5(),
                    'SHA1':entry.get_hash_sha1(),
                    'SHA256':entry.get_hash_sha256(),
                    'SHA512':entry.get_hash_sha512()}
            table.append(sect)
            sect = {}
        return table


    ## Return dump_dict() for debug only
    def dump(self):
        return self.pedict
