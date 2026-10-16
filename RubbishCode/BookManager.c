#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

struct Book{
    char* Name;
    char* Author;
    char* Publisher;
    long int ISBN;
    float Price;
    struct Book* next;
};

void FlushBuffer(void); //Finished

bool IsISBNExist(struct Book*,long int); //Finished

char* ReadString(void); //Finished

void AddNewBook(struct Book**); //Finished

void RemoveBook(struct Book**); //Finished

void SearchBook(struct Book*); //Finished

void ListBooks(struct Book*); //Finished

void ExportBooks(struct Book*);//Finished

int main(int argc,char* argv[])
{
    struct Book* MainDatabase = NULL;
    if(argc < 2)
    {
        goto Init;
    }
    FILE *fp = fopen(argv[1],"r");
    if(fp == NULL)
    {
        goto Init;
    }
    char Buffer[3072] = {0};
    char _Name[1024] = {0};
    char _Author[1024] = {0};
    char _Publisher[1024] = {0};
    long int _ISBN = 0;
    float _Price = 0;
    int Counting = 0;
    while(fgets(Buffer,3072,fp) != NULL)
    {
        Counting++;
        if((sscanf(Buffer,"书名：%1023[^\t]\t作者：%1023[^\t]\t出版社：%1023[^\t]\tISBN：%ld\t价格：%f",_Name, _Author, _Publisher, &_ISBN, &_Price)) != 5)
        {
            fprintf(stderr,"错误：导出文件格式错误，跳过第%d行\n",Counting);
            continue;
        }
        struct Book* newnode = malloc(sizeof(struct Book));
        if(newnode == NULL)
        {
            fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
            exit(1);
        }
        newnode -> next = MainDatabase;
        MainDatabase = newnode;
        char* MallocName = malloc((strlen(_Name) + 1) * sizeof(char));
        if(MallocName == NULL)
        {
            fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
            exit(1);
        }
        strcpy(MallocName,_Name);
        newnode -> Name = MallocName;
        char* MallocAuthor = malloc((strlen(_Author) + 1) * sizeof(char));
        if(MallocAuthor == NULL)
        {
            fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
            exit(1);
        }
        strcpy(MallocAuthor,_Author);
        newnode -> Author = MallocAuthor;
        char* MallocPublisher = malloc((strlen(_Publisher) + 1) * sizeof(char));
        if(MallocPublisher == NULL)
        {
            fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
            exit(1);
        }
        strcpy(MallocPublisher,_Publisher);
        newnode -> Publisher = MallocPublisher;
        newnode -> ISBN = _ISBN;
        newnode -> Price = _Price;
    }
    fclose(fp);
    Init:;
    int input = 0;
    int auth = 0;
    Tag1:
    printf("1：注册新书籍\n2：删除指定书籍\n3：列出指定书籍\n4：列出所有书籍\n5：导出书籍至文件\n6：从文件加载书籍\n0：退出程序\n请输入编号：");
    auth = scanf("%d",&input);
    if(auth != 1)
    {
        fprintf(stderr,"非法输入！\n");
        FlushBuffer();
        goto Tag1;
    }
    FlushBuffer();
    for(;;)
    {
        switch(input)
        {
            case 1:AddNewBook(&MainDatabase);goto Tag1;
            case 2:RemoveBook(&MainDatabase);goto Tag1;
            case 3:SearchBook(MainDatabase);goto Tag1;
            case 4:ListBooks(MainDatabase);goto Tag1;
            case 5:ExportBooks(MainDatabase);goto Tag1;
            case 6:printf("要从文件加载书籍，将导出文件的绝对路径和文件名当作本程序的参数传入。\n");goto Tag1;;
            case 0:exit(0);
            default:printf("非法输入！\n");goto Tag1;
        }
    }
    return 0;

}

void FlushBuffer(void)
{
    int _ = 0;
    for(;(_ = getchar()) != '\n' && _ != EOF;);
    if(_ == EOF)
    {
        clearerr(stdin);
    }
    return;
}

char* ReadString(void)
{
    char Buffer[10240] = {0};
    fgets(Buffer,sizeof(Buffer),stdin);
    if(Buffer[10238] != 0)
    {
        printf("输入过长，已截断。\n");
        FlushBuffer();
    }
    else if(strchr(Buffer,'\n') == NULL)
    {
        printf("警告：无换行的异常输入。\n");
        FlushBuffer();
    }
    int Length = strlen(Buffer);
    char* FinalString = malloc(Length + 1);
    if(FinalString == NULL)
    {
        fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
        exit(1);
    }
    strcpy(FinalString,Buffer);
    if((Length >= 1) && (FinalString[Length - 1] == '\n'))
    {
        FinalString[Length - 1] = '\0';
    }
    return FinalString;
}

void AddNewBook(struct Book** firstd)
{
    if(firstd == NULL)
    {
        fprintf(stderr,"错误：空指针。\n");
        return;
    }
    struct Book* first = *firstd;
    if(first == NULL)
    {
        first = malloc(sizeof(struct Book));
        if(first == NULL)
        {
            fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
            exit(1);
        }
        printf("请输入书名：");
        first -> Name = ReadString();
        printf("请输入作者：");
        first -> Author = ReadString();
        printf("请输入出版社：");
        first -> Publisher = ReadString();
        int auth = 0;
        Tag1:
        printf("请输入价格：");
        auth = scanf("%f",&(first -> Price));
        if(auth != 1)
        {
            fprintf(stderr,"非法输入！\n");
            FlushBuffer();
            goto Tag1;
        }
        FlushBuffer();
        Tag2:
        printf("请输入ISBN：");
        auth = scanf("%ld",&(first -> ISBN));
        if(auth != 1)
        {
            fprintf(stderr,"非法输入！\n");
            FlushBuffer();
            goto Tag2;
        }
        FlushBuffer();
        first -> next = NULL;
        *firstd = first;
        return;
    }
    struct Book* newnode = malloc(sizeof(struct Book));
    if(newnode == NULL)
        {
            fprintf(stderr,"错误：堆内存已满。\n堆内存已满，无法维系程序正常运行，程序中止。\n");
            exit(1);
        }
    printf("请输入书名：");
    newnode -> Name = ReadString();
    printf("请输入作者：");
    newnode -> Author = ReadString();
    printf("请输入出版社：");
    newnode -> Publisher = ReadString();
    int auth = 0;
    Tag3:
    printf("请输入价格：");
    auth = scanf("%f",&(newnode -> Price));
    if(auth != 1)
    {
        fprintf(stderr,"非法输入！\n");
        FlushBuffer();
        goto Tag3;
    }
    FlushBuffer();
    Tag4:
    printf("请输入ISBN：");
    auth = scanf("%ld",&(newnode -> ISBN));
    if(auth != 1)
    {
        fprintf(stderr,"非法输入！\n");
        FlushBuffer();
        goto Tag4;
    }
    FlushBuffer();
    if(IsISBNExist(first,newnode -> ISBN))
    {
        printf("错误：ISBN已存在！\n");
        goto Tag4;
    }
    newnode -> next = NULL;
    struct Book *cur = first;
    while(cur -> next != NULL)
    {
        cur = cur -> next;
    }
    cur -> next = newnode;
    return;
}
//Not complete yet
